import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

INCLUDE_DIRECTIVE_NAME = 'include_directives'


@pytest.mark.datafiles(SAMPLES_DIR / INCLUDE_DIRECTIVE_NAME, keep_top_dir=True)
def test_include_directive_project(datafiles, tmp_path):
    project_path = datafiles / INCLUDE_DIRECTIVE_NAME

    results = do_analysis(project_path, tmp_path)

    expected_result = [
        {'filename': 'index.php', 'lineNumber': 9, 'allowedTypes': [], 'allowedClasses': [
            'Literal', 'Magic', 'Builtin', 'GlobClassThree', 'GlobClassTwo', 'GlobClassOne']},
        {'filename': 'src/BackwardsToo.php', 'lineNumber': 9,
            'allowedTypes': [], 'allowedClasses': ['BackwardsToo', 'Backwards']},
        {'filename': 'src/ConstantIncl.php', 'lineNumber': 7,
            'allowedTypes': [], 'allowedClasses': ['Constant']}
    ]
    assert compare_results(expected_result, results)


INCLUDE_TEST_NAME = 'include_test'


@pytest.mark.datafiles(SAMPLES_DIR / INCLUDE_TEST_NAME, keep_top_dir=True)
def test_include_test_project(datafiles, tmp_path):
    project_path = datafiles / INCLUDE_TEST_NAME

    results = do_analysis(project_path, tmp_path)

    # TODO make custom check function that doesn't depend on class order
    expected_result1 = [{'filename': 'CClass.php', 'lineNumber': 32, 'allowedTypes': [], 'allowedClasses': [
        'CClass', 'BClass', 'A2Class', 'A1Class', 'D1Class', 'D2Class', 'FClass', 'EClass']}]

    expected_result2 = [{'filename': 'CClass.php', 'lineNumber': 32, 'allowedTypes': [], 'allowedClasses': [
        'CClass', 'BClass', 'A1Class', 'A2Class', 'D1Class', 'D2Class', 'FClass', 'EClass']}]

   # assert (check_objects(expected_result1, results) or check_objects(expected_result2, results))
   # assert (results == expected_result1 or results == expected_result2)
    assert compare_results(expected_result1, results)


AUTOLOAD_PROJECT_NAME = 'autoload_test'


@pytest.mark.datafiles(SAMPLES_DIR / AUTOLOAD_PROJECT_NAME, keep_top_dir=True)
def test_autoload_project(datafiles, tmp_path):
    project_path = datafiles / AUTOLOAD_PROJECT_NAME

    results = do_analysis(project_path, tmp_path)

    expected_result = [{'filename': 'index.php', 'lineNumber': 19, 'allowedTypes': [
        'string', '', 'AnotherClass', 'MyClass', 'MyOtherClass'], 'allowedClasses': []}]
    assert compare_results(expected_result, results)
