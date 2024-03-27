import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

# TODO test actual feature with more in depth output checks

CONDITIONS_FIELD_GET_NAME = "conditions_field_get.php"


@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONS_FIELD_GET_NAME)
def test_conditions_field_get(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_FIELD_GET_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {'filename': 'conditions_field_get.php', 'lineNumber': 15,
            'allowedTypes': ['', 'SomeClass'], 'allowedClasses': ['SomeClass']}
    ]

    assert compare_results(expected_result, results)


CONDITIONS_FIELD_SET_NAME = "conditions_field_set.php"


@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONS_FIELD_SET_NAME)
def test_conditions_field_set(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_FIELD_SET_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'conditions_field_set.php',
                        'lineNumber': 15, 'allowedTypes': ['SomeClass'], 'allowedClasses': ['SomeClass']}]

    assert compare_results(expected_result, results)


CONDITIONS_TEST_NAME = "conditions_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONS_TEST_NAME)
def test_conditions_test(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'conditions_test.php', 'lineNumber': 8,
                        'allowedTypes': ['string', ''], 'allowedClasses': []}]

    assert compare_results(expected_result, results)


CONDITIONS_TWO_CALLS_TEST_NAME = "conditions_two_calls_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONS_TWO_CALLS_TEST_NAME)
def test_conditions_two_calls_test(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_TWO_CALLS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'conditions_two_calls_test.php',
                        'lineNumber': 30, 'allowedTypes': ['LoneClass'], 'allowedClasses': ['LoneClass']},
                       {'filename': 'conditions_two_calls_test.php', 'lineNumber': 34,
                        'allowedTypes': ['FirstCall'], 'allowedClasses': ['FirstCall']}
                       ]
    assert compare_results(expected_result, results)


CONDITIONALS_TEST_NAME = "conditionals_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONALS_TEST_NAME)
def test_conditionals(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONALS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'conditionals_test.php', 'lineNumber': 6,
                        'allowedTypes': ['string', ''], 'allowedClasses': []},
                       {'filename': 'conditionals_test.php', 'lineNumber': 10,
                           'allowedTypes': ['string', ''], 'allowedClasses': []},
                       {'filename': 'conditionals_test.php', 'lineNumber': 14,
                           'allowedTypes': [], 'allowedClasses': []},
                       {'filename': 'conditionals_test.php', 'lineNumber': 20,
                           'allowedTypes': [], 'allowedClasses': []},
                       {'filename': 'conditionals_test.php', 'lineNumber': 24,
                           'allowedTypes': ['string', ''], 'allowedClasses': []},
                       {'filename': 'conditionals_test.php', 'lineNumber': 28,
                           'allowedTypes': ['string', ''], 'allowedClasses': []},
                       {'filename': 'conditionals_test.php', 'lineNumber': 32,
                           'allowedTypes': [], 'allowedClasses': []}
                       ]
    assert compare_results(expected_result, results)
