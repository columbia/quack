import pytest

from .conftest import SAMPLES_DIR
from .utils import compare_results, do_analysis

DUCK_TEST_PROJECT = "duck_test"
DUCK_TEST_NAME = "duck_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / DUCK_TEST_PROJECT, keep_top_dir=True)
def test_simple_project(datafiles, tmp_path):
    project_path = datafiles / DUCK_TEST_PROJECT

    results = do_analysis(project_path, tmp_path)

    expected_result = [
        {
            "filename": DUCK_TEST_NAME,
            "lineNumber": 20,
            "allowedTypes": ["Duck"],
            "allowedClasses": [],
        }
    ]

    assert compare_results(expected_result, results, project_path)
