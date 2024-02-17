import argparse
import dataclasses
import json
import logging
import sys
from pathlib import Path
from subprocess import run, CompletedProcess
from time import time

import colorama

from deduce_allowed_classes import compute_allowed_classes
from pyutils.common_functions import get_elapsed_str, make_sure_dir_exists

logging.root.setLevel(logging.INFO)

fhandler = logging.FileHandler("debug.log")
fhandler.setLevel(logging.DEBUG)
fhandler.setFormatter(logging.Formatter("[%(levelname)s] %(message)s"))
handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.INFO)
handler.setFormatter(logging.Formatter("[%(levelname)s] %(message)s"))
my_logger = logging.getLogger(__name__)
my_logger.setLevel(logging.DEBUG)
my_logger.addHandler(handler)
my_logger.addHandler(fhandler)


def warn(msg):
    my_logger.debug(f"{colorama.Fore.RED}[WARNING] {msg}{colorama.Style.RESET_ALL}")


def info(msg):
    my_logger.debug(f"{colorama.Fore.GREEN}[INFO] {msg}{colorama.Style.RESET_ALL}")


# Data class representing a call to a deserialization API
class UnserCallLocation:
    filename: str
    line: int
    startFilePos: int
    endFilePos: int

    def __init__(self, filename, line, startFilePos, endFilePos):
        self.filename = filename
        self.line = int(line)
        self.startFilePos = int(startFilePos)
        self.endFilePos = int(endFilePos)

    def __eq__(self, other):
        return (
                self.filename == other.filename and self.line == other.line and self.startFilePos == other.startFilePos and self.endFilePos == other.endFilePos)

    def __hash__(self):
        return hash((self.filename, self.line, self.startFilePos, self.endFilePos))

    def __ne__(self, other):
        return not (self == other)

    def __repr__(self):
        return f"<{self.filename}, line {self.line}, pos {self.startFilePos}-{self.endFilePos}>"


# Main runner class
@dataclasses.dataclass
class PHPAnalyzer:
    project_path: Path
    results_path: Path

    def __post_init__(self):
        self.process_project()

    # "Public" methods

    def process_project(self):
        try:
            # Make sure the provided project exists
            if not self.project_path.exists():
                my_logger.error(f"Project path({self.project_path}) does not exist")
                return

            make_sure_dir_exists(self.results_path)
            reported_times = {}
            # For Debug: Put lines here to reduce analysis only to this line
            focus_lines = []

            # Run the Joern analysis
            my_logger.info("Running joern analysis")
            # Path to save the JOERN CPG graph for the project
            joe_out_graph = self.results_path / "JOEGRAPH"

            def run_report_time(args, log_prefix, no_exit=False):
                start_time = time()
                joe_parse_cmd_ret: CompletedProcess = run(args, capture_output=True)
                reported_times[log_prefix] = get_elapsed_str(start_time)

                cmd_stdout = joe_parse_cmd_ret.stdout.decode("utf-8")
                cmd_stderr = joe_parse_cmd_ret.stderr.decode("utf-8")
                my_logger.debug(
                    f"[{log_prefix}] CMD=[{args}]\n===:STDOUT:===\n{cmd_stdout}\n===:STDERR:===\n{cmd_stderr}")
                if joe_parse_cmd_ret.returncode != 0:
                    my_logger.error(f"{log_prefix} failed. Got retcode:{joe_parse_cmd_ret.returncode}. Stopping")
                    if not no_exit:
                        import pdb
                        pdb.set_trace()
                        exit()
                else:
                    my_logger.info(f"{log_prefix} finished successfully")

            run_report_time(["joern-parse", self.project_path, "--language", "php", "--output", joe_out_graph],
                            "Joern-Prase (graph creation)")

            analysis_results_path = self.results_path.joinpath("joe_analyze.out")
            joern_analyze_params = ["joern", "--script", Path("tools", "analyze.sc"), "--param",
                                    f"cpgFile={joe_out_graph}", "--param", f"outFile={analysis_results_path}"]
            if len(focus_lines) > 0:
                focus_lines = ",".join([f"{x[0]}:{x[1]}" for x in focus_lines])
                my_logger.debug(f"Focus lines: {focus_lines}")
                joern_analyze_params.extend(["--param", f"focus_lines={focus_lines}"])
            run_report_time(joern_analyze_params, "Joern-Script[Analyze]", True)

            avail_res_path = self.results_path.joinpath("availclass.json")
            psr4_path = Path("tools", "helpers", "get_psr4_mappings.php")
            run_report_time(
                ["joern", "--script", Path("tools", "resolve_includes.sc"), "--param", f"cpgFile={joe_out_graph}",
                 "--param", f"outFile={avail_res_path}", "--param", f"psr4Script={psr4_path}"],
                "Joern-Script[AvailClasses]")

            with self.results_path.joinpath("runtime_info.json").open("w") as f:
                f.write(json.dumps(reported_times, indent=True))

            # Read the results produced from each QUACK sub-component
            with analysis_results_path.open() as f:
                evidence_entries = json.load(f)

            # TODO: remove this after the fix inside availclass script
            with avail_res_path.open() as f:
                avail_classes_entries = json.load(f)
                for l in avail_classes_entries:
                    for k, i in l.items():
                        if k == "filename":
                            my_p = Path(i)
                            l[k] = my_p.relative_to(self.project_path).as_posix()

            with self.results_path.joinpath("availclass_fixed.json").open("w") as f:
                f.write(json.dumps(avail_classes_entries, indent=True))
            # END OF TODO

            # Consolidate the available classes with the Psalm analysis results
            result_entries = compute_allowed_classes(evidence_entries, avail_classes_entries)  # there should only be one project..

            # Write final results to results JSON file
            with self.results_path.joinpath("results.json").open("w") as f:
                f.write(json.dumps(result_entries))


        except ValueError as e:
            my_logger.error(f"{e.__class__.__name__}:{e}")


def main():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument("project_path", help="Path to project to analyze", type=Path)
    arg_parser.add_argument("--output-path", type=Path, default=None,
                            help="Path for keeping Quack's outputs (defaults to project path)")
    args = arg_parser.parse_args()

    # User requested to analyze a specific project
    if not args.project_path.exists():
        my_logger.error(f"Project path {args.project_path} doesnt exist")
        exit()
    project_path = args.project_path
    output_path = args.project_path if args.output_path is None else args.output_path

    PHPAnalyzer(project_path, output_path)


if __name__ == "__main__":
    main()
