import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

# TODO expand to actually test comment type detection
COMMENT_TYPES_TEST_NAME = "comment_types_test.php"


@pytest.mark.datafiles(SAMPLES_DIR / COMMENT_TYPES_TEST_NAME)
def test_comment_types_test(datafiles, tmp_path):
    fragment_path = datafiles / COMMENT_TYPES_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'comment_types_test.php',
                        'lineNumber': 8, 'allowedTypes': ['mixed'], 'allowedClasses': []}]

    assert compare_results(expected_result, results)


COMMENT_TYPES_TEST2_NAME = "comment_types_test2.php"


@pytest.mark.datafiles(SAMPLES_DIR / COMMENT_TYPES_TEST2_NAME)
def test_comment_types_test2(datafiles, tmp_path):
    fragment_path = datafiles / COMMENT_TYPES_TEST2_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'comment_types_test2.php',
                        'lineNumber': 30, 'allowedTypes': ['float'], 'allowedClasses': []}]

    assert compare_results(expected_result, results)
