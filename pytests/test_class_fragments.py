import pytest

from .conftest import SAMPLES_DIR
from .utils import compare_results, do_analysis

CLASS_TEST_NAME = "class_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / CLASS_TEST_NAME)
def test_class_test(datafiles, tmp_path):
    fragment_path = datafiles / CLASS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            "filename": "class_test.php",
            "lineNumber": 8,
            "allowedTypes": ["bool"],
            "allowedClasses": [],
        }
    ]

    assert compare_results(expected_result, results, fragment_path)


CLASS_THIS_TEST_NAME = "class_this_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / CLASS_THIS_TEST_NAME)
def test_class_this_test(datafiles, tmp_path):
    fragment_path = datafiles / CLASS_THIS_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            "filename": "class_this_test.php",
            "lineNumber": 6,
            "allowedTypes": [],
            "allowedClasses": ["Person"],
        }
    ]

    assert compare_results(expected_result, results, fragment_path)


CLASS_STORAGE_TEST_NAME = "class_storage_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / CLASS_STORAGE_TEST_NAME)
def test_class_storage_test(datafiles, tmp_path):
    fragment_path = datafiles / CLASS_STORAGE_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {
            "filename": "class_storage_test.php",
            "lineNumber": 33,
            "allowedTypes": ["string", "Template", "InterestingClass", ""],
            "allowedClasses": ["InterestingClass"],
        },
        {
            "filename": "class_storage_test.php",
            "lineNumber": 29,
            "allowedTypes": ["InterestingClass"],
            "allowedClasses": ["InterestingClass"],
        },
    ]

    assert compare_results(expected_result, results, fragment_path)
