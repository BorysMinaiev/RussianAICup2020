import json
import subprocess

v1 = 23
v2 = 24


def run_one_game():
    port1 = 32001
    port2 = 32002

    process = subprocess.Popen(["/Users/bminaiev/Downloads/aicup2020-macos/batch-test.sh",
                                str(v1), str(v2), str(port1), str(port2)], stdout=subprocess.PIPE)
    output, error = process.communicate()

    results = json.loads(output)

    seed = results['seed']
    score1 = results['results'][0]
    score2 = results['results'][1]

    return score1, score2, seed


wins1 = 0
wins2 = 0

print("games\t{}\t{}\tseed\tscore1\tscore2".format(v1, v2))

TOTAL_GAMES = 100

for it in range(TOTAL_GAMES):
    score1, score2, seed = run_one_game()

    if score1 > score2:
        wins1 += 1
    elif score2 > score1:
        wins2 += 1

    print("{}\t{}\t{}\t{}\t{}\t{}".format(it + 1, wins1, wins2, seed, score1, score2))
