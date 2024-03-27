import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

LIST_TEST_NAME = "list-statement.php"
# Note: this test currently fails, see Github Issue #23


@pytest.mark.datafiles(SAMPLES_DIR / LIST_TEST_NAME)
def test_list_construct(datafiles, tmp_path):
    fragment_path = datafiles / LIST_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {'filename': 'list-statement.php', 'lineNumber': 20,
            'allowedTypes': ['FooClass'], 'allowedClasses': ['FooClass']},
        {'filename': 'list-statement.php', 'lineNumber': 24,
            'allowedTypes': ['FooClass'], 'allowedClasses': ['FooClass']},
        {'filename': 'list-statement.php', 'lineNumber': 28,
            'allowedTypes': ['BarClass'], 'allowedClasses': ['BarClass']},
        {'filename': 'list-statement.php', 'lineNumber': 33,
            'allowedTypes': ['FooClass', 'BarClass'], 'allowedClasses': ['FooClass', 'BarClass']},
    ]

    assert compare_results(expected_result, results)


AST_PARENTS_TEST_NAME = "AST_parents_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / AST_PARENTS_TEST_NAME)
def test_AST_parents_test(datafiles, tmp_path):
    fragment_path = datafiles / AST_PARENTS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)
    expected_result = [
        {'filename': 'AST_parents_test.php', 'lineNumber': 12,
            'allowedTypes': [], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 15,
            'allowedTypes': [], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 19,
            'allowedTypes': [], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 22,
            'allowedTypes': [], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 27,
            'allowedTypes': [''], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 32,
            'allowedTypes': ['bool'], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 37,
            'allowedTypes': ['', 'string'], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 41,
            'allowedTypes': ['mixed'], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 46, 'allowedTypes': [
            '<operator>.instanceOf.<operator>.conditional.<returnValue>', '', 'string'], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 54,
            'allowedTypes': ['string'], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 58,
            'allowedTypes': ['string', ''], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 66,
            'allowedTypes': ['string', ''], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 69,
            'allowedTypes': ['array', 'mixed'], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 74,
            'allowedTypes': [], 'allowedClasses': []},
        {'filename': 'AST_parents_test.php', 'lineNumber': 80,
            'allowedTypes': [], 'allowedClasses': []}
    ]

    assert compare_results(expected_result, results)


FUGIO_INSPIRED_TEST_NAME = "fugio_inspired_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / FUGIO_INSPIRED_TEST_NAME)
def test_fugio_inspired_test(datafiles, tmp_path):
    fragment_path = datafiles / FUGIO_INSPIRED_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'fugio_inspired_test.php',
                        'lineNumber': 8, 'allowedTypes': [''], 'allowedClasses': []}]

    assert compare_results(expected_result, results)


GETTYPE_SWITCH_TEST_NAME = "gettype_switch_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / GETTYPE_SWITCH_TEST_NAME)
def test_gettype_switch_test(datafiles, tmp_path):
    fragment_path = datafiles / GETTYPE_SWITCH_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            'filename': 'gettype_switch_test.php',
            'lineNumber': 6,
            'allowedTypes': ['string', 'mixed', 'bool', 'mixed', 'float', 'string', 'int', 'mixed', 'array', 'array', '', 'mixed'],
            'allowedClasses': []
        }
    ]

    assert compare_results(expected_result, results)


MAYBE_UNSERIALIZE_TEST_NAME = "maybe_unserialize_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / MAYBE_UNSERIALIZE_TEST_NAME)
def test_maybe_unserialize_test(datafiles, tmp_path):
    fragment_path = datafiles / MAYBE_UNSERIALIZE_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'maybe_unserialize_test.php',
                        'lineNumber': 6, 'allowedTypes': ['string', ''], 'allowedClasses': []}]

    assert compare_results(expected_result, results)


RECURSIVE_VAR_USE_TEST_NAME = "recursive_var_use_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / RECURSIVE_VAR_USE_TEST_NAME)
def test_recursive_var_use_test(datafiles, tmp_path):
    fragment_path = datafiles / RECURSIVE_VAR_USE_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {'filename': 'recursive_var_use_test.php', 'lineNumber': 4,
            'allowedTypes': ['string', '', 'mixed'], 'allowedClasses': []},
        {'filename': 'recursive_var_use_test.php', 'lineNumber': 17,
            'allowedTypes': ['', 'string'], 'allowedClasses': []},
        {'filename': 'recursive_var_use_test.php', 'lineNumber': 21,
            'allowedTypes': ['numeric'], 'allowedClasses': []}
    ]

    assert compare_results(expected_result, results)


TOSTRING_TEST_NAME = "tostring_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / TOSTRING_TEST_NAME)
def test_tostring_test(datafiles, tmp_path):
    fragment_path = datafiles / TOSTRING_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'tostring_test.php', 'lineNumber': 24, 'allowedTypes': [
        'string', 'mixed', 'string', 'HasToString', 'mixed'], 'allowedClasses': []}]

    assert compare_results(expected_result, results)
