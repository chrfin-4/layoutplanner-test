These are the 200 problem instances and 5 solver configs that were
used to generate data for the ETFA 2021 paper, together with the raw
results.

The problem instances are planning requests in JSON format. The file
names are 123-XYZ.json, where 123 is the seed that was used, and XYZ
is just the count (from 0 to 199).

The problems were generated with the current version of the
ProblemGenerator (which includes all the parameters) and the specified
seed (123).

The results file has the format:
`time,init,hard,soft,config`
where time is in milliseconds.
