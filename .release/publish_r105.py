#!/usr/bin/env python3
"""Fail closed: the single-addon revision105 publisher was never dispatched.

The complete prior script remains in Git history at295550a96d4a9cd7c2201c4ab13a41289f64b5d6.
Combined PR308/revision106 preserves FluffyMachines, JEG and ExtraHeads.
No release, core binary, tag or live-server file is changed by this guard.
"""
raise SystemExit(
    'Revision105 publication is disabled. Validate and publish the exact combined '
    'revision106 source in Slimefun-Legacy PR308; do not overwrite concurrent addon updates.'
)
