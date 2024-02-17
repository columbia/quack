import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results


DOCBLOCK_SIMPLE_NAME = "docblock_simple.php"


@pytest.mark.datafiles(SAMPLES_DIR / DOCBLOCK_SIMPLE_NAME)
def test_docblock_simple(datafiles, tmp_path):
    fragment_path = datafiles / DOCBLOCK_SIMPLE_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'docblock_simple.php', 'lineNumber': 26,
                        'allowedTypes': ['mixed'], 'allowedClasses': ['ClassA', 'ClassB']}]

    assert compare_results(expected_result, results)


DOCBLOCK_TIEBREAK_NAME = "docblock_tiebreak.php"


@pytest.mark.datafiles(SAMPLES_DIR / DOCBLOCK_TIEBREAK_NAME)
def test_docblock_tiebreak(datafiles, tmp_path):
    fragment_path = datafiles / DOCBLOCK_TIEBREAK_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [
        {'filename': 'docblock_tiebreak.php', 'lineNumber': 51, 'allowedTypes': [
            'ClassA', 'ClassC', 'mixed'], 'allowedClasses': ['ClassC', 'ClassA']}
    ]

    assert compare_results(expected_result, results)


DOCBLOCK_RETURN_INCONSISTENT_TEST_NAME = "docblock_return_inconsistent.php"


@pytest.mark.datafiles(SAMPLES_DIR / DOCBLOCK_RETURN_INCONSISTENT_TEST_NAME)
@pytest.mark.skip()
def test_docblock_return_inconsistent(datafiles, tmp_path):
    fragment_path = datafiles / DOCBLOCK_RETURN_INCONSISTENT_TEST_NAME

    results = do_analysis(fragment_path, tmp_path)

    expected_result = []

    assert compare_results(expected_result, results)
