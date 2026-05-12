# Shortest Grave

[![CI Tests](https://github.com/FIrgolitsch/shortest-grave/actions/workflows/tests.yml/badge.svg)](https://github.com/FIrgolitsch/shortest-grave/actions/workflows/tests.yml)
[![Lint](https://github.com/FIrgolitsch/shortest-grave/actions/workflows/lint.yml/badge.svg)](https://github.com/FIrgolitsch/shortest-grave/actions/workflows/lint.yml)

Triggers shortest path to plot a path to your gravestone after death.

## Features
- Automatically plots a path to your gravestone after death.
- Set a custom path colour to indicate the gravestone path.
- Optionally enable to always plot a path, even though no gravestone was spawned.

## Not Planned
- Support for multiple gravestones. Shortest path only plans one route at a time.
- Wilderness Check. Adding this check is non-trival and would require a lot of code duplication from Shortest Path.
  - Use the Shortest Path settings to cofigure this behaviour.