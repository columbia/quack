# Quack

Quack is a code analysis tool to help PHP developers mitigate the risk of
deserialization vulnerabilities in their applications. Quack restricts
the set of classes that a deserialized object is allowed to take. Quack
determines the set of allowed classes through _static duck typing_.

For more detailed information about using static duck typing to mitigate
deserialization vulnerabilities, please see our paper
[2024 NDSS paper](https://www.ndss-symposium.org/wp-content/uploads/2024-1015-paper.pdf).
The evaluated artifact for the paper can be found
[here](https://figshare.com/articles/software/QUACK_Hindering_Deserialization_Attacks_via_Static_Duck_Typing/24578644). Also see  appendix XII in our paper for more details.

Quack is implemented as analysis passes over the
[Joern static code analysis tool](https://github.com/joernio/joern).

## Overview

Quack works in three steps:

1. Quack identifies the set of all available classes in the PHP module at the
   time of a call to `unserialize`;
2. Quack identifies all uses of the object returned from `unserialize`;
3. Quack performs static duck typing analysis to determine the set of allowed
   classes for the deserialized object based on how the object is used in Step
   2, and outputs the set of allowed classes.

The programmer can use the set of allowed classes output by Quack as an
optional [`allowed_classes` argument](https://www.php.net/manual/en/function.unserialize.php)
to the `unserialize` function call.

## Requirements and Setup

Quack depends on:
* [Joern code analysis platform](https://joern.io/) version 2.0.156
* Java Development Kit 19 (Joern dependency)
* Python3

Quack depends on features we introduced to Joern version 2.0.156. Older versions
of Joern will not work, while newer versions of Joern likely will work,
although have not been explicitly tested.


To setup Quack on an Ubuntu 22.04 system:

1. Install JDK 19.

```
$ sudo apt-get update && sudo apt-get install -y openjdk-19-jdk openjdk-19-jre
```

2. Install Joern.

```
$ curl -L "https://github.com/joernio/joern/releases/latest/download/joern-install.sh" -o joern-install.sh
$ chmod u+x joern-install.sh
$ ./joern-install.sh --version=v2.0.156
```

3. Install Python packages (optionally, in a virtual environment).

```
$ python3 -m venv .venv
$ source .venv/bin/activate
$ python3 -m pip install -r requirements.txt
```
We also provide a [Dockerfile](Dockerfile) with an example setup for reference.

If the installation was successful, you should be able to run all our system's tests using the
[pytest command](https://docs.pytest.org/en/7.1.x/reference/reference.html#command-line-flags)
(add `-s` to see Quack output during tests):

```
$ pytest
```

## Usage

Quack is invoked as a Python runner.py executable:

```
$ python3 runner.py --help
usage: runner.py [-h] [--output-path OUTPUT_PATH] project_path

positional arguments:
  project_path          Path to project to analyze

options:
  -h, --help            show this help message and exit
  --output-path OUTPUT_PATH
                        Path for keeping Quack's outputs (defaults to project path)
```

To analyze a PHP project located at path `~/projects/target-project`, run Quack
with:

```
$ python3 runner.py --output-path ./analysis-results ~/projects/target-project
```

This will result in files created in the `./analysis-results` directory, and a
printed output statement displaying the set of allowed types that Quack
identified.

## Tests

The `pytests` directory contains Python tests that run Quack on a variety of PHP
samples (found in `pytests\php-samples`).

To run these tests, first set up Quack
[as described previously](#requirements-and-setup) and run the
[pytest command](https://docs.pytest.org/en/7.1.x/reference/reference.html#command-line-flags)
(add `-s` to see Quack output during tests):

```
$ pytest
```

## DocBlock type hint handling

At the current time, Quack does not support using DocBlock comments as type
hint information to guide the analysis. If this is functionality that is
important to your use case, please open an issue in our repository for
discussion.
