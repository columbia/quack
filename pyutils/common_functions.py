import argparse
import errno
import functools
import getpass
import logging
import os
import shutil
import subprocess
import sys
import tarfile
import typing as _typ
import zipfile
from dataclasses import dataclass
from io import IOBase
from multiprocessing import Pool
from os import makedirs, getcwd, cpu_count
from os.path import isdir, sep, basename, join as path_join, relpath
from pathlib import Path
from re import compile as re_compile
from shutil import rmtree
from time import strftime, gmtime, time, struct_time, strptime

from chardet import detect

### encoding stuff ###

RE_EXP_NO_NON_ASCI = re_compile(r'[^\x00-\x7F]')


def safely_decode_text(path: Path) -> str:
    with path.open('rb') as b_bf:
        rawdata = b_bf.read()
        if len(rawdata) == 0:
            return ""
        encoding = detect(rawdata)['encoding']
        if encoding is None:
            return rawdata.decode(errors='replace')
        try:
            return rawdata.decode(encoding)
        except UnicodeDecodeError:
            return rawdata.decode(errors='replace')


def replace_unicode(text):
    return RE_EXP_NO_NON_ASCI.sub(f"??", text)


"""input is either a str or path and will return a Path object"""
def path_maker(path) -> Path:
    if isinstance(path, str):
        return Path(path)
    return path


### TIME STRINGS STUFF ####

def get_current_strdate(include_time=False):
    return get_datestr_from_struct_time(gmtime(), include_time)


def get_datestr_from_epoc(seconds_since_epoc: int, include_time=False) -> str:
    return get_datestr_from_struct_time(gmtime(seconds_since_epoc), include_time)


def get_datestr_from_struct_time(st: struct_time, include_time=False) -> str:
    assert (isinstance(st, struct_time))
    if include_time:
        return strftime("%Y-%m-%d_%H-%M-%S", st)
    else:
        return strftime("%Y-%m-%d", st)


def get_struct_time_from_datestr(datestr: str, include_time=False) -> struct_time:
    if include_time:
        return strptime(datestr, "%Y-%m-%d_%H-%M-%S")
    else:
        return strptime(datestr, "%Y-%m-%d")


########################

def mapper_is_serial(mapper):
    return mapper is map

def create_imapper(args, pool_to_use=None, para_level=None):
    if args['num_cores'] > 1:
        logging.debug("Creating a pool with {} cores for mapper".format(args['num_cores']))
        if pool_to_use is None:
            pool_to_use = Pool(args['num_cores'])
        mapper = functools.partial(pool_to_use.imap_unordered)
    else:
        logging.debug("Creating a non-parallelizable mapper (1 core)")
        mapper = map

    if para_level is not None:
        if args['para_level'] == 1:
            out_mapper = mapper
            in_mapper = map
        elif args['para_level'] == 2:
            out_mapper = map
            in_mapper = mapper
        else:
            logging.error("Only levels 1 & 2 are supported.")
            exit()
        return out_mapper, in_mapper
    else:
        return mapper


def get_graceful_worker(worker, exit_on_error=False):
    def out(cr):
        try:
            return worker(cr)
        except Exception as e:
            logging.exception(f"!!graceful_worker!![{str(cr)}] - {e.__class__.__name__}:{e}")
            if exit_on_error:
                exit()

    return out


########################

profiling_outfile = "cProfile.perf"


def init_leak_finder(args):
    if 1 == 0 and args['find_leaks']:
        from pympler import tracker
        memory_tracker = tracker.SummaryTracker()
        logging.info("Running in mem leak finder mode")
        return memory_tracker


def init_profiling(args):
    if args['cprofile']:
        import cProfile
        pr = cProfile.Profile()
        pr_start = time()
        pr.bias = 1.22781772182e-06
        pr.enable()
        logging.info("Running in cProfile mode")
        return {'pr': pr, 'pr_start': pr_start}


def do_parser_init(parser):
    # This should be called in the main scripts init() procedure, after the script-specific args have been added
    args = vars(parser.parse_args())
    if args['debug']:
        logging.root.setLevel(logging.DEBUG)
    elif args['verbose']:
        logging.root.setLevel(logging.INFO)
    else:
        logging.root.setLevel(logging.WARNING)

    leak_tracker = init_leak_finder(args)
    profile = init_profiling(args)

    return args, profile, leak_tracker


def add_common_args_to_parser(parser):
    parser.add_argument('--keep-temps', action='store_const', const=True, help="Keep temp files")
    num_cores_argument = parser.add_argument('--num-cores', type=int, default=cpu_count(),
                                             help='set the number of cores to use')

    # use this action to set 'num-cores' to 1
    class OneCoreAction(argparse.Action):
        def __call__(self, parser, namespace, values, option_string=None):
            setattr(namespace, self.dest, values)
            setattr(namespace, num_cores_argument.dest, 1)

    parser.add_argument('-1', action=OneCoreAction, help='Use one core', nargs=0)
    parser.add_argument('-p', '--para-level', type=int, default=2,
                        help='At what point to start forking threads (default is lower-level==2)')

    parser.add_argument('-v', '--verbose', action='store_const', const=True, help='Be verbose')
    parser.add_argument('--debug', action='store_const', const=True, help='Enable debug prints')
    parser.add_argument('-r', '--reversed', action='store_true', help='Reverse job order when relevant')

    parser.add_argument('--cprofile', action='store_const', const=True, default=False,
                        help="run cProfile and output to file {}".format(profiling_outfile))

    parser.add_argument('--find-leaks', action='store_true',
                        help="run mem-leak finder and print".format(profiling_outfile))


def run_as_main(name, script_path, main, init):
    if name == '__main__':
        try:
            original_dir = getcwd()
            input_args = None  # set this so it will exist in the case of exception
            input_args, pr, lk = init()
            main(input_args)

        except Exception as e:
            logging.critical(f"`run_as_main` STOPPING DUE TO EXCEPTION \n {e.__class__.__name__}: {str(e)}")
            logging.exception(msg=e, exc_info=True)
            exit()

        if input_args['cprofile']:
            pr['pr'].disable()
            profile_filename = "{}_{}".format(get_current_strdate(), profiling_outfile)
            logging.info(
                "Writing profile file {}. TOTALTIME={}".format(profile_filename, get_elapsed_str(pr['pr_start'])))
            os.chdir(original_dir)
            pr['pr'].dump_stats(profile_filename)

        if 1 == 0 and input_args['find_leaks']:
            lk.print_diff()


def add_include_exclude_args(parser):
    parser.add_argument('--include', type=str, default=None, help="Must include string | (comma separated strings)")
    parser.add_argument('--exclude', type=str, default=None, help="Exclude this(es) string | (comma separated strings)")

def include_exclude_yielder(members_sequence, include: str, exclude: str, key=None):
    includes = include.split(",") if include is not None else include
    excludes = exclude.split(",") if exclude is not None else []

    for member in members_sequence:
        if key is not None:
            try:
                member_rep = key(member)
            except Exception:
                logging.warning(f"Error getting key for {member}. Skipping")
        else:
            member_rep = member

        if any(map(lambda x: x in member_rep, excludes)):
            continue
        if include is not None:
            if not any(map(lambda x: x in member_rep, includes)):
                continue
        yield member


########## ZIP STUFF ######


def load_zip(path):
    try:
        z = zipfile.ZipFile(path, 'r')
    except (zipfile.BadZipfile, IOError) as e:  # not a zip
        return None
    return z


def directory_into_zip(zip_obj, path):
    path_base_name = basename(path)
    for root, dirs, files in os.walk(path):
        for f in files:
            zip_obj.write(path_join(root, f), arcname=path_join(path_base_name, relpath(root, path), f))


def extract_any_archive(archive: _typ.Union[Path, IOBase], extract_to: Path, extract_to_name: str = None,
                        file_name: str = None):
    """
    This function will extract archives from any type!
    In the basic mode, where only archive and extract_to are set, a simple extraction will be made.

    In the advance mode, we try to place all files and dirs in the archive inside a directory named extract_to_name,
    and return the name of the first part (Path.parts) for the first object in the archive

    :param archive: Can be path or IOBase. If IObase, file_name must be specified.
    :param extract_to: Root path to extract into. Note that any subdirs from the archive will be created under this path
    :param [Optional] extract_to_name: Assume all files in archive are in one dir and make sure extracted dir will have
                            `extract_to_name` as name.
    :param [Optional] file_name:  See archive specification.

    """
    if isinstance(archive, Path):
        io_stream = archive.open("rb")
        if file_name is None:
            file_name = archive.name
    elif isinstance(archive, IOBase):
        io_stream = archive
        if file_name is None:
            raise ValueError("If IObase, file_name must be specified.")
    else:
        raise ValueError(f"`archive` arg is wrong type [{type(archive)}], should be Union[Path, IOBase]")

    if any(map(lambda x: file_name.endswith(x), [".zip"])):
        with zipfile.ZipFile(io_stream) as z:
            rename_from = Path(z.namelist()[0]).parts[0]
            z.extractall(extract_to)
    elif any(map(lambda x: file_name.endswith(x), ["tar.gz", ".tgz", "tar.bz2"])):
        with tarfile.open(name=None, fileobj=io_stream) as t:
            if extract_to_name is not None:
                try:
                    mem = t.next()
                    if not mem.isdir():
                        # this is very MacOS specific - sometimes when taring under MacOS we get these attribute files
                        # inside the tar as new files start with "." and having the same name as the file.
                        # these will appear before the folder, so we can check it.
                        raise ValueError("Tar is bad. look inside.")
                    else:
                        rename_from = Path(mem.path).parts[0]
                except:
                    rename_from = None
            t.extractall(extract_to)
    else:
        raise ValueError(f"Unknown suffix {file_name}")

    if extract_to_name is not None:
        if rename_from is None:
            raise ValueError("Dir not found")
        if rename_from != extract_to_name:
            extract_to.joinpath(rename_from).rename(extract_to.joinpath(extract_to_name))
        return rename_from


### dir stuff ###


def safe_rmtree(path):
    if path is not None and isdir(path):
        try:
            rmtree(path)
        except OSError:
            pass


def real_basedir(path):
    if path.endswith(sep):
        return basename(path[:-1])
    return basename(path)


def make_sure_dir_exists(path: _typ.Union[str, Path], delete_if_exists: bool = False) -> Path:
    if isinstance(path, Path):
        return _make_sure_path_exists(path, delete_if_exists)
    elif isinstance(path, str):
        raise ValueError("Port to Path. The time is now.")
    else:
        raise ValueError("path should be Path or str")


def _make_sure_path_exists(path: Path, delete_if_exists: bool) -> Path:
    if path.is_dir():
        if delete_if_exists:
            rmtree(path.as_posix(), ignore_errors=True)
            makedirs(path.as_posix(), exist_ok=True)
    else:
        makedirs(path, exist_ok=True)
    return path


def get_glob_match(glob_root: Path, glob_str: str, allow_empty=False, exclude=None) -> Path:
    r = list(glob_root.glob(glob_str))
    if exclude is not None:
        filtered = list(filter(lambda x: not exclude(x.as_posix()), r))
    else:
        filtered = r
    if len(filtered) == 0:
        if allow_empty:
            return None
        else:
            raise ValueError(f"{glob_str} not found in {glob_root.as_posix()}")
    elif len(filtered) == 1:
        return filtered[0]
    else:
        raise ValueError(f"{glob_str} has {len(filtered)} finds in {glob_root.as_posix()}: [{filtered}], (r={r})")


def create_tmpfs(module_name, size=None, multiplier=0.2, remove_old=True):
    if create_tmpfs.tmp_files_path:
        return create_tmpfs.tmp_files_path

    if size:
        if not (type(size) == str and (size.endswith('m') or size.endswith('g'))):
            print("bad formed size for tmpfs (try something like '500m' or '1g')")
            assert (False)
    tmpfs_dir = create_tmpfs.tmpfs_dir

    def rmtree_handler(function, path, excinfo):
        try:
            os.remove(path)
        except OSError as remove_e:
            if remove_e.errno != errno.ENOENT:
                raise

    if not os.path.isdir(tmpfs_dir):
        os.mkdir(tmpfs_dir)
        if not size:  # by default allocate 20% or mem for tmpfs
            from psutil import virtual_memory
            size = int(virtual_memory().total * multiplier)
        logging.warning("Creating TMPFS of size {}MB - you might need to provide a password".format(size / 1024))
        subprocess.call(['sudo', 'mount', '-t', 'tmpfs', '-o', 'size={}'.format(size), 'tmpfs', tmpfs_dir])

    tmp_files_base_path = os.path.join(tmpfs_dir, getpass.getuser())
    if not os.path.isdir(tmp_files_base_path):
        os.mkdir(tmp_files_base_path)

    # make a subdir so that different modules won't collide
    tmp_files_path = os.path.join(tmp_files_base_path, module_name)
    if remove_old and os.path.isdir(tmp_files_path):
        shutil.rmtree(tmp_files_path, onerror=rmtree_handler)
    if not os.path.isdir(tmp_files_path):
        os.mkdir(tmp_files_path)

    create_tmpfs.tmp_files_path = tmp_files_path
    return tmp_files_path


create_tmpfs.tmpfs_dir = "/tmp/tmpfs/"
create_tmpfs.tmp_files_path = None


@dataclass
class ChangeDir:
    target_dir: _typ.Union[_typ.AnyStr, Path]

    def __enter__(self):
        self.original_dir = getcwd()
        os.chdir(self.target_dir)
        return self.original_dir

    def __exit__(self, exc_type, exc_val, exc_tb):
        os.chdir(self.original_dir)


### helpers & debuggers ###

class NoOutput:
    def __enter__(self):
        self.original_stdout = sys.stdout
        self.original_stderr = sys.stderr
        nl = open(os.devnull, "w")
        sys.stdout = nl
        sys.stderr = nl

    def __exit__(self, exc_type, exc_val, exc_tb):
        nl = sys.stderr
        nl.close()
        sys.stdout = self.original_stdout
        sys.stderr = self.original_stderr


# TODO: make this into a context...
def get_elapsed_str(start_time):
    end = time()
    hours, rem = divmod(end - start_time, 3600)
    minutes, seconds = divmod(rem, 60)
    return "{:0>2}:{:0>2}:{:05.2f}".format(int(hours), int(minutes), seconds)


class ElapsedTime:
    def __enter__(self):
        # TODO: make this into a context...
        self.start_time = time()
        return self

    def get_elpased_hours(self) -> int:
        hours, rem = divmod(time() - self.start_time, 3600)
        return int(hours)

    def __exit__(self, exc_type, exc_val, exc_tb):
        hours, rem = divmod(time() - self.start_time, 3600)
        minutes, seconds = divmod(rem, 60)
        self.get_elapsed_str = "{:0>2}:{:0>2}:{:05.2f}".format(int(hours), int(minutes), seconds)


def call_me_maybe(exception_to_throw=None):
    # freebritney
    import inspect
    for frame in inspect.stack():
        if frame[1].endswith("pydevd.py"):
            import pydevd
            pydevd.settrace()

    got_it = False
    breakpoint()
    if not got_it:
        if exception_to_throw is None:
            raise hell
        else:
            raise exception_to_throw


# prefixed_string_generator

class PrefixedStringGenerator:
    def __init__(self, limit=None):
        self.existing = set()
        self.limit = limit

    def __call__(self, *args, **kwarg):
        count = 0
        arg_list = list(args)
        while True:
            name = "_".join(arg_list + [str(count)])
            if name not in self.existing:
                self.existing.add(name)
                break
            count += 1
            if self.limit is not None and count > self.limit:
                raise GeneratorLimitReached()
        return name

    def remove(self, name):
        if name in self.existing:
            self.existing.remove(name)


class GeneratorLimitReached(Exception):
    pass


#### Other ####

def clipped_error_str(s, len_to_clip=500, len_to_keep_from_each_side=200):
    if not isinstance(s, str):
        s = str(s)
    if len(s) > len_to_clip:
        return f"Clipped Error\n: {s[:len_to_keep_from_each_side]}\n...\n{s[-1 * len_to_keep_from_each_side:]}"
    else:
        return s
