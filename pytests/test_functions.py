import pytest
from .conftest import SAMPLES_DIR
from .utils import do_analysis, compare_results

LOCAL_ARG_CONFLICT = "local-arg-conflict.php"

# Note this test is only confirmed to work on Joern 2.0.290, and fails on 2.0.156
@pytest.mark.datafiles(SAMPLES_DIR / LOCAL_ARG_CONFLICT)
def test_local_arg_conflict(datafiles, tmp_path):
    fragment_path = datafiles / LOCAL_ARG_CONFLICT

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'local-arg-conflict.php', 'lineNumber': 11,
                      'allowedTypes': ['string', '', 'mixed'], 'allowedClasses': []}]


    assert compare_results(expected_result, results)


LOCAL_ARG_NOCONFLICT = "local-arg-noconflict.php"


@pytest.mark.datafiles(SAMPLES_DIR / LOCAL_ARG_NOCONFLICT)
def test_local_arg_noconflict(datafiles, tmp_path):
    fragment_path = datafiles / LOCAL_ARG_NOCONFLICT

    results = do_analysis(fragment_path, tmp_path)

    expected_result = [{'filename': 'local-arg-noconflict.php', 'lineNumber': 11,
                      'allowedTypes': ['string', '', 'mixed'], 'allowedClasses': []}]


    assert compare_results(expected_result, results)
