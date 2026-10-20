#!/usr/bin/env python3
"""Regression tests for the UNLOCK_* token checks in check_tsv.py.

A typo'd 'UNLOCK_<NAME>=1' Items token used to reach the Java parser and
kill plugin startup; check_tsv.py now rejects unknown unlock names and any
quantity other than the literal '=1'. These tests pin that contract:
 - the '=1' grammar check is unconditional (it must still fire when the
   Unlock.java enum source cannot be read — fail-open applies to the name
   check only),
 - the enum-name check is driven by the live Unlock.java, not a hardcoded
   list,
 - committed resource data must keep passing (no false rejections).

Run: python3 -m unittest scripts/test_check_tsv.py   (from the repo root)
 or: python3 scripts/test_check_tsv.py              (from anywhere)
Stdlib only — matches check_tsv.py's no-new-dependencies constraint.
"""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

import check_tsv

# A controlled name set keeps the validator tests independent of the exact
# Unlock.java contents; load_unlock_names() itself is exercised separately.
FAKE_NAMES = {"XERICS_HONOUR", "CANOE_AXE", "BALLOON_LOG_BASKET"}


def make_tsv(directory, items_cell):
    """Write a minimal transports-shaped TSV into directory and return its path."""
    path = os.path.join(directory, "fixture.tsv")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# Origin\tDestination\tDuration\tItems\n")
        f.write("3200 3200 0\t3300 3300 0\t5\t" + items_cell + "\n")
    return path


def run_linter(script_path, resource_dir):
    return subprocess.run(
        [sys.executable, script_path, resource_dir],
        capture_output=True, text=True,
    )


class ValidateItemsUnlockGrammarTest(unittest.TestCase):
    """The '=1' quantity contract and the enum-name check on single cells."""

    def setUp(self):
        self._orig = check_tsv.UNLOCK_NAMES
        check_tsv.UNLOCK_NAMES = set(FAKE_NAMES)

    def tearDown(self):
        check_tsv.UNLOCK_NAMES = self._orig

    def test_unknown_unlock_name_is_rejected(self):
        errors = check_tsv.validate_items("UNLOCK_TYPO=1")
        self.assertTrue(errors, "a typo'd unlock name must produce an error")
        self.assertTrue(
            any("UNLOCK_TYPO" in e for e in errors),
            "the error must name the offending token: %r" % errors,
        )

    def test_known_unlock_name_passes(self):
        self.assertEqual([], check_tsv.validate_items("UNLOCK_XERICS_HONOUR=1"))

    def test_quantity_two_is_rejected(self):
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR=2")
        self.assertTrue(any("=1" in e for e in errors),
                        "expected an '=1' grammar error: %r" % errors)

    def test_quantity_zero_is_rejected(self):
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR=0")
        self.assertTrue(any("=1" in e for e in errors),
                        "expected an '=1' grammar error: %r" % errors)

    def test_leading_zero_quantity_is_rejected(self):
        # '=1' is a string-level contract: '01' is not the literal '1'.
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR=01")
        self.assertTrue(any("=1" in e for e in errors),
                        "expected an '=1' grammar error: %r" % errors)

    def test_negative_quantity_is_rejected(self):
        # '-1' fails the NAME=qty shape entirely — still a hard error.
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR=-1")
        self.assertTrue(errors)

    def test_non_numeric_quantity_is_rejected(self):
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR=abc")
        self.assertTrue(errors)

    def test_missing_quantity_is_rejected(self):
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR")
        self.assertTrue(errors)

    def test_bad_unlock_inside_or_group_is_rejected(self):
        errors = check_tsv.validate_items("995=5|UNLOCK_XERICS_HONOUR=2")
        self.assertTrue(any("=1" in e for e in errors))

    def test_bad_unlock_inside_and_group_is_rejected(self):
        errors = check_tsv.validate_items("995=5&UNLOCK_TYPO=1")
        self.assertTrue(any("UNLOCK_TYPO" in e for e in errors))

    def test_normalization_matches_the_parser(self):
        # Spaces, lowercase, and '||'/'&&' collapse the same way
        # ItemRequirementParser normalizes — a disguised typo still fails.
        errors = check_tsv.validate_items(" unlock_typo = 1 ")
        self.assertTrue(any("UNLOCK_TYPO" in e for e in errors),
                        "normalized typo must still be caught: %r" % errors)
        errors = check_tsv.validate_items("995=5 || UNLOCK_XERICS_HONOUR = 2")
        self.assertTrue(any("=1" in e for e in errors))

    def test_empty_cell_produces_no_errors(self):
        self.assertEqual([], check_tsv.validate_items(""))

    def test_ordinary_items_still_pass(self):
        self.assertEqual([], check_tsv.validate_items("995=5"))
        self.assertEqual([], check_tsv.validate_items("13393=1&995=5"))


class FailOpenTest(unittest.TestCase):
    """When Unlock.java cannot be read the name check skips, but the '=1'
    grammar check must keep firing."""

    def setUp(self):
        self._orig = check_tsv.UNLOCK_NAMES
        check_tsv.UNLOCK_NAMES = None

    def tearDown(self):
        check_tsv.UNLOCK_NAMES = self._orig

    def test_quantity_check_survives_missing_enum(self):
        errors = check_tsv.validate_items("UNLOCK_XERICS_HONOUR=2")
        self.assertTrue(any("=1" in e for e in errors),
                        "'=1' enforcement must not depend on the enum: %r" % errors)

    def test_name_check_skips_when_enum_missing(self):
        self.assertEqual([], check_tsv.validate_items("UNLOCK_TYPO=1"))


class UnlockNamesLoadingTest(unittest.TestCase):
    """The name set must come from the live Unlock.java, never a literal."""

    def test_enum_members_load_from_source(self):
        names = check_tsv.load_unlock_names()
        self.assertIsNotNone(names, "Unlock.java must be readable next to the script")
        for member in ("XERICS_HONOUR", "CANOE_AXE", "BALLOON_LOG_BASKET",
                       "DRAGONTOOTH", "KARAMJA_ELITE", "MOR_UL_REK"):
            self.assertIn(member, names)

    def test_empty_scan_fails_open(self):
        # A reformatted/misparsed Unlock.java yielding zero members must not
        # produce an authoritative empty set that rejects every token.
        orig = check_tsv.UNLOCK_JAVA
        try:
            with tempfile.TemporaryDirectory() as tmp:
                stub = os.path.join(tmp, "Unlock.java")
                with open(stub, "w", encoding="utf-8") as f:
                    f.write("public enum Unlock { /* no parseable members */ }\n")
                check_tsv.UNLOCK_JAVA = stub
                self.assertIsNone(check_tsv.load_unlock_names())
        finally:
            check_tsv.UNLOCK_JAVA = orig


class EndToEndTest(unittest.TestCase):
    """The linter as the CI workflow invokes it: a directory of TSVs."""

    def test_typo_fixture_fails_lint(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_tsv(tmp, "UNLOCK_TYPO=1")
            result = run_linter(os.path.join(SCRIPT_DIR, "check_tsv.py"), tmp)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("UNLOCK_TYPO", result.stdout)

    def test_legal_unlock_fixture_passes_lint(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_tsv(tmp, "UNLOCK_XERICS_HONOUR=1")
            result = run_linter(os.path.join(SCRIPT_DIR, "check_tsv.py"), tmp)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_committed_resources_have_no_unlock_regressions(self):
        # Every committed UNLOCK_* token is '=1' and names a real enum
        # member — the lint must stay green on the shipped data.
        result = run_linter(
            os.path.join(SCRIPT_DIR, "check_tsv.py"), check_tsv.DEFAULT_DIR)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("unknown unlock token", result.stdout)

    def test_script_detached_from_enum_still_enforces_grammar(self):
        # Run a copy of the script where ../src/.../Unlock.java does not
        # exist: the stderr note must print, '=2' must still fail, and a
        # well-formed '=1' token with a bogus name must pass.
        with tempfile.TemporaryDirectory() as tmp:
            script_dir = os.path.join(tmp, "scripts")
            os.makedirs(script_dir)
            script_copy = os.path.join(script_dir, "check_tsv.py")
            shutil.copy(os.path.join(SCRIPT_DIR, "check_tsv.py"), script_copy)

            bad_qty_dir = os.path.join(tmp, "bad_qty")
            os.makedirs(bad_qty_dir)
            make_tsv(bad_qty_dir, "UNLOCK_XERICS_HONOUR=2")
            bad = run_linter(script_copy, bad_qty_dir)
            self.assertIn("Unlock.java", bad.stderr)
            self.assertEqual(1, bad.returncode, bad.stdout + bad.stderr)
            self.assertIn("=1", bad.stdout)

            typo_dir = os.path.join(tmp, "typo")
            os.makedirs(typo_dir)
            make_tsv(typo_dir, "UNLOCK_TYPO=1")
            typo = run_linter(script_copy, typo_dir)
            self.assertIn("Unlock.java", typo.stderr)
            self.assertEqual(0, typo.returncode,
                             "name check must fail open: " + typo.stdout + typo.stderr)


if __name__ == "__main__":
    unittest.main()
