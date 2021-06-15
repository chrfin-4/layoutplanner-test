The point of this sub-project/sub-module is to separate the layout planner
project into two parts:

- production
- research

This is roughly analogous to optaplanner-core and optaplanner-benchmark. The
main production project should only contain what is needed for solving layout
planning problems. Any experimentation, possibly for the purpose of writing
papers, is then done in this project to avoid cluttering up the production
code.

See the etfa21 branch for code and files related to the data presented in
the ETFA 2021 paper describing the layout planner.
