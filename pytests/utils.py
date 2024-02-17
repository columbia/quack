from runner import PHPAnalyzer
import json
from .conftest import RESULTS_FNAME


def do_analysis(project_path, result_path):
    PHPAnalyzer(project_path=project_path, results_path=result_path)

    with open(result_path / RESULTS_FNAME) as results_file:
        results = json.loads(results_file.read().strip())
    print("Test Results:", results)
    return results

def print_failure(expected, actual, reason: str):
    print("Comparison failed:", reason)
    print("Expected:", expected)
    print("Actual:", actual)

def sort_by_line_number(results: list[dict]) -> list[dict]:
    return sorted(results, key=lambda x: x['lineNumber'])

def compare_call_result(expected: dict, actual: dict) -> bool:
    if expected['filename'] != actual['filename']:
        print_failure(expected, actual, "Filename mismatch")
        return False
    if expected['lineNumber'] != actual['lineNumber']:
        print_failure(expected, actual, "Line number mismatch")
        return False
    
    all_expected = set(expected['allowedTypes'] + expected['allowedClasses'])
    all_actual = set(actual['allowedTypes'] + actual['allowedClasses'])

    # TODO should really prevent empty strings from being added in the first place
    all_expected.discard('')
    all_actual.discard('')

    if(all_expected != all_actual):
        print_failure(expected, actual, f"Allowed types and classes mismatch, difference: {all_expected.symmetric_difference(all_actual)}")
        return False

    # TODO remove these checks, they are mostly for debugging
    expected_types = set(expected['allowedTypes'])
    actual_types = set(actual['allowedTypes'])
    if (expected_types != actual_types):
        print_failure(expected, actual, f"(Test still passes) allowed types mismatch, difference: {expected_types.symmetric_difference(actual_types)}")
    
    expected_classes = set(expected['allowedClasses'])
    actual_classes = set(actual['allowedClasses'])
    if (expected_classes != actual_classes):
        print_failure(expected, actual, f"(Test still passes) Allowed classes mismatch, difference: {expected_classes.symmetric_difference(actual_classes)}")
    
    return True

def compare_results(expected, actual) -> bool:
    if len(expected) != len(actual):
        print_failure(expected, actual, "Number of results not equal")
        return False
    
    sorted_expected = sort_by_line_number(expected)
    sorted_actual = sort_by_line_number(actual)

    for i, expected_result in enumerate(sorted_expected):
        actual_result = sorted_actual[i]
        if compare_call_result(expected_result, actual_result) is False:
            return False

    return True
