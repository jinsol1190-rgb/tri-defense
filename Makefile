PYTHON ?= python3
VENV := .venv
VENV_PYTHON := $(VENV)/bin/python
FORGE ?= $(if $(wildcard .tools/forge),.tools/forge,forge)

.PHONY: help setup build build-python build-contracts test test-python test-contracts check

help:
	@echo "make setup  - create isolated Python environment and install existing package"
	@echo "make build  - build Python wheel and existing Solidity contracts"
	@echo "make test   - run existing Python tests and Solidity format/tests"
	@echo "make check  - build and test; requires make setup first"
	@echo "This root target covers Python/Contracts only; see module READMEs for Android, Backend and Dashboard."

setup:
	$(PYTHON) -m venv $(VENV)
	$(VENV_PYTHON) -m pip install .
	$(VENV_PYTHON) -m pip check

build: build-python build-contracts

build-python:
	$(VENV_PYTHON) -m pip wheel --no-deps --wheel-dir dist .

build-contracts:
	$(FORGE) build

test: test-python test-contracts

test-python:
	$(VENV_PYTHON) -m unittest discover -s tests -v

test-contracts:
	$(FORGE) fmt --check
	$(FORGE) test -vv

check: build test
