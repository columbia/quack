import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

IMPORT_HELPERS = "analysis_tests_helpers"

# TODO eventually check in more depth to make sure required stuff found
MID_FILE_IMPORT_TEST_NAME = "mid_file_import_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / MID_FILE_IMPORT_TEST_NAME,
                       SAMPLES_DIR / IMPORT_HELPERS, keep_top_dir=True)
def test_mid_file_import_test(datafiles, tmp_path):
    fragment_path = datafiles / MID_FILE_IMPORT_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'mid_file_import_test.php', 'lineNumber': 6, 'allowedTypes': [], 'allowedClasses': []}, {'filename': 'mid_file_import_test.php',
                                                                                                                             'lineNumber': 15, 'allowedTypes': [], 'allowedClasses': []}, {'filename': 'mid_file_import_test.php', 'lineNumber': 18, 'allowedTypes': [], 'allowedClasses': []}]

    assert compare_results(expected_result, results)
