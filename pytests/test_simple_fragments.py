"""Tests on small, simple fragments of PHP code, which don't import anything"""
import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

# Test simple string case
SIMPLE_PRINT_NAME = 'simple-print.php'


@pytest.mark.datafiles(SAMPLES_DIR / SIMPLE_PRINT_NAME)
def test_simple_print(datafiles, tmp_path):
    fragment_path = datafiles / SIMPLE_PRINT_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            "filename": SIMPLE_PRINT_NAME,
            "lineNumber": 4,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        }
    ]

    assert compare_results(expected_result, results)


TEST_PRINT_LIST_NAME = "list_print_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / TEST_PRINT_LIST_NAME)
@pytest.mark.skip()
def test_print_list(datafiles, tmp_path):
    fragment_path = datafiles / TEST_PRINT_LIST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            "filename": TEST_PRINT_LIST_NAME,
            "lineNumber": 4,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        }
    ]

    assert compare_results(expected_result, results)


# Test two simple string cases
TWO_PRINTS_NAME = "two-simple-prints.php"


@pytest.mark.datafiles(SAMPLES_DIR / TWO_PRINTS_NAME)
def test_two_prints(datafiles, tmp_path):
    fragment_path = datafiles / TWO_PRINTS_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            "filename": TWO_PRINTS_NAME,
            "lineNumber": 4,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        },
        {
            "filename": TWO_PRINTS_NAME,
            "lineNumber": 8,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        }
    ]

    assert compare_results(expected_result, results)
