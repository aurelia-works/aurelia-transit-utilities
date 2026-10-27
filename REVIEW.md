# Review rules (Aurelia Transit Utilities)

Start every review with one line: `N issues` or `No issues.`

Every finding cites `path:line` and says what goes wrong, with the input or world state that triggers it. A finding without a file and line is dropped.

## Real failures here
- A change that needs ATU on the server, or sends a packet the server must handle. ATU is client-only.
- A train preset that saves or loads a siding's settings (acceleration, deceleration, max trains, manual mode, delay handling) wrongly, or for the wrong siding.
- A preset or config write that is not atomic, or that overwrites the file after a failed read.
- MTR data read or written off the thread that owns it, or used without a null check for a missing station or route.
- The ATU button on MTR's dashboard opening the wrong screen or breaking MTR's map.
- A test edited or weakened so it passes.

## Ignore
- Style, naming and formatting that match the surrounding code.
- MTR gson log noise such as `unknown string mtr:train_cargo_*`.
- Output of the dev-only self-test (`src/selftest`) when it is not a failure.
- Suggestions that expand the change beyond its goal (list them as follow-ups).
