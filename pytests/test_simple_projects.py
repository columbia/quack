"""Tests on small, simple fragments of PHP code, which don't import anything"""
import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

# Test simple string case inside a project
SIMPLE_PROJECT_NAME = 'simple-print-project'
SIMPLE_PRINT_NAME = 'simple-print.php'


@pytest.mark.datafiles(SAMPLES_DIR / SIMPLE_PROJECT_NAME, keep_top_dir=True)
def test_simple_project(datafiles, tmp_path):
    project_path = datafiles / SIMPLE_PROJECT_NAME

    results = do_analysis(project_path, tmp_path)

    expected_result = [
        {
            "filename": SIMPLE_PRINT_NAME,
            "lineNumber": 4,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        }
    ]

    assert compare_results(expected_result, results)


# Test simple string case inside a project
MULTI_PROJECT_NAME = 'multi-print-project'


@pytest.mark.datafiles(SAMPLES_DIR / MULTI_PROJECT_NAME, keep_top_dir=True)
def test_multi_project(datafiles, tmp_path):
    project_path = datafiles / MULTI_PROJECT_NAME

    results = do_analysis(project_path, tmp_path)

    expected_result = [
        {
            "filename": "simple-print-one.php",
            "lineNumber": 4,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        },
        {
            "filename": "simple-print-two.php",
            "lineNumber": 4,
            "allowedTypes": ['string', ''],
            "allowedClasses": []
        }
    ]

    assert compare_results(expected_result, results)
