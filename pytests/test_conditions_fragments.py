import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

# TODO test actual feature with more in depth output checks

CONDITIONS_FIELD_GET_NAME = "conditions_field_get.php"

@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONS_FIELD_GET_NAME)
@pytest.mark.skip()
def test_conditions_field_get(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_FIELD_GET_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {'filename': 'conditions_field_get.php', 'lineNumber': 6,
            'allowedTypes': [''], 'allowedClasses': []}
    ]

    assert compare_results(expected_result, results)


CONDITIONS_FIELD_SET_NAME = "conditions_field_set.php"


@pytest.mark.datafiles(SAMPLES_DIR / CONDITIONS_FIELD_SET_NAME)
@pytest.mark.skip()
def test_conditions_field_set(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_FIELD_SET_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'conditions_field_set.php',
                        'lineNumber': 6, 'allowedTypes': [''], 'allowedClasses': []}]

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
@pytest.mark.skip()
def test_conditions_two_calls_test(datafiles, tmp_path):
    fragment_path = datafiles / CONDITIONS_TWO_CALLS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'conditions_two_calls_test.php',
                        'lineNumber': 6, 'allowedTypes': ['', ''], 'allowedClasses': []}]

    assert compare_results(expected_result, results)
