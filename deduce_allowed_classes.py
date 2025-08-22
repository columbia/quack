"""
deduce_allowed_classes.py

Given the set of classes available to an attacker at a deserialization call
site, and the evidence collected of how the deserialized object is used
(through static-duck-typing algorithm), determine the set of classes that
deserialized object should be allowed to express.
"""

import itertools
from collections import defaultdict

# PHP primitive types.
# Don't treat string as native because of __toString
native_types = ["int", "bool", "boolean", "float"]

# PHP types (and analysis artifacts - ANY) that do not provide useful inference
# information.
not_useful_types = ["any", "ANY", "mixed", "array", "null"]


class Leak(Exception):
    """
    When an object is used in a method that cannot be resolved statically
    (e.g., the call target is dynamically resolved), we cannot infer anything
    about the object's type and we consider this a "Leak" of the type
    inference. Raise this exception to indicate that this has occurred.
    """

    pass


def deduce_allowed_classes(avail_classes, grouped_evidence):
    # Extract all type deductions from the evidence collected about an object.

    all_obj_types = []
    all_obj_types_no_tostring = []
    # If we have more than one evidence for a single object, take the intersection
    # of all deduced types
    for obj_evidence in grouped_evidence:
        obj_evidence_no_tostring = [
            x for x in obj_evidence if x["reason"] != "HasToString"
        ]

        # Check if the type leaks because it's being used in a dynamic call, and if it
        # does, return all available classes
        leaks = any([x["reason"] == "DynamicCall" for x in obj_evidence])
        if leaks:
            raise Leak()

        obj_types = list(map(
            set,
            [
                evidence["type"].split("|")
                for evidence in obj_evidence
                if len(evidence["type"]) != 0
            ],
        ))
        obj_types_no_tostring = list(map(
            set,
            [
                evidence["type"].split("|")
                for evidence in obj_evidence_no_tostring
                if len(evidence["type"]) != 0
            ],
        ))

        if len(obj_types) > 0:
            obj_types_intersect = set.intersection(*obj_types)
            all_obj_types.append(obj_types_intersect)

        if len(obj_types_no_tostring) > 0:
            obj_types_intersect_no_tostring = set.intersection(
                *obj_types_no_tostring)
            all_obj_types_no_tostring.append(obj_types_intersect_no_tostring)

    # No types found
    if len(all_obj_types) == 0:
        return [], [], []

    # Check if we have evidence indicating a native type
    have_native_evidence = any([x in native_types for x in all_obj_types])

    # Allowed types are the intersection of the set of types deduced for an
    # object, and the set of available classes.
    allowed_types_all = set(
        [
            obj_type
            for obj_type_set in all_obj_types
            for obj_type in obj_type_set
            if obj_type in avail_classes
        ]
    )
    allowed_types_no_tostring = set(
        [
            obj_type
            for obj_type_set in all_obj_types_no_tostring
            for obj_type in obj_type_set
            if obj_type in avail_classes
        ]
    )

    # We should not have found evidence that the object is both a native and
    # a class type.
    if have_native_evidence and len(allowed_types_no_tostring) > 0:
        raise Exception(
            f"Found evidence for native type but also the following allowed types: {allowed_types_all}"
        )

    # Determine whether the deduced types are useful for constraining the
    # available classes into allowed classes.
    # Filter out the evidence for types that don't give us any useful type
    # information (e.g., if type is 'array', we don't actually know what the
    # types of each element are, unless we collected more evidence)
    types_all = set.union(*all_obj_types)
    useful_types = [x for x in types_all if x not in not_useful_types]
    useful_types = [x for x in useful_types if "." not in x and "->" not in x]

    # print(f"Useful types: {useful_types}")

    # Found evidence that the object is a native type, so allow no classes.
    if have_native_evidence:
        allowed_types_all = []
        allowed_types_no_tostring = []
    # Found no useful evidence to determine the type of the object, so allow
    # all available classes.
    elif len(useful_types) == 0:
        allowed_types_no_tostring = avail_classes

    return list(types_all), list(allowed_types_all), list(allowed_types_no_tostring)


def compute_allowed_classes(evidence_entries, avail_classes_entries):

    # Iterate through every deserialization call information produced by the
    # type-inference analysis and further trim down the list of allowed classes
    # based on the set of available classes at that callsite
    result_entries = []
    for unser_call in evidence_entries:
        # Get the call location
        filename = unser_call["filename"]
        line_no = unser_call["lineNumber"]

        print(f"Working on: File[{filename}],Line[{line_no}]")

        # Get the collected type evidence
        evidence = [
            x for x in unser_call["conditions"] if x["condType"] in ("Duck", "Exact")
        ]

        # Group by object
        grouped_evidence_dict = defaultdict(list)
        for item in evidence:
            grouped_evidence_dict[item["nodeId"]].append(item)
        grouped_evidence = list(grouped_evidence_dict.values())

        # Get the available classes at that callsite
        avail_classes_entry = [
            x for x in avail_classes_entries if x["filename"] == filename
        ]
        assert (
            len(avail_classes_entry) == 1
        ), f"No avail classes entries found for {filename}!"

        avail_classes_entry = avail_classes_entry[0]
        avail_classes_lines = list(
            set(avail_classes_entry["line_numbers"]).intersection({line_no})
        )
        # Make sure we actually have available classes for this line
        assert (
            len(avail_classes_lines) >= 1
        ), f"No avail classes entries found for {line_no} in {filename}!"
        avail_classes = avail_classes_entry["avail_classes"]
        result_entries.append(
            {
                "filename": filename,
                "lineNumber": line_no,
                "allowedTypes": None,
                "allowedClasses": None,
            }
        )
        try:
            # Call the main script that consolidates the available classes
            # with the inferred types and produces the final set of allowed classes
            filtered_grouped_evidence = [
                [{key: d.get(key) for key in ("type", "reason")}
                 for d in obj_evidence]
                for obj_evidence in grouped_evidence
            ]
            types_all, allowed_classes_all, allowed_types_no_tostring = (
                deduce_allowed_classes(
                    avail_classes, filtered_grouped_evidence)
            )
            print(f"All types collected from evidence: {types_all}")
            print(f"All allowed classes: {allowed_types_no_tostring}")
            result_entries[-1]["allowedTypes"] = types_all
            result_entries[-1]["allowedClasses"] = list(
                allowed_types_no_tostring)
        except Leak as e:
            # we identify that the analysis is "leaking" (e.g., flows in to a dynamic
            # call we can't track)=> we need to just return available classes' gadgets.
            # When not taking into account the available classes (NOAVAIL), we just return all the gadgets in the project,
            # else only return the gadgets in the available classes
            print(
                f"Project analysis for [{filename}]:[{line_no}] resulted in a leak. "
                f"See docs about how to continue from here"
            )
        except Exception as e:
            print(f"{e.__class__.__name__}:{e}")

    return result_entries


if __name__ == "__main__":

    import argparse
    import json

    parser = argparse.ArgumentParser()
    parser.add_argument("--analysis-results-path", required=True)
    parser.add_argument("--availclass-results-path", required=True)
    args = parser.parse_args()

    with open(args.analysis_results_path) as f:
        evidence_entries = json.load(f)

    with open(args.availclass_results_path) as f:
        avail_classes_entries = json.load(f)

    results = compute_allowed_classes(evidence_entries, avail_classes_entries)
    print(results)
