import optuna
import subprocess
import json
import os
import sys

# Define target JSON path
LOSS_PATH = "core/domain-solver/build/tuning_loss.json"
BEST_WEIGHTS_PATH = "core/domain-solver/build/best_weights.json"

def objective(trial):
    # Propose weights to tune
    weights = {
        "RECOVERY_PRIORITY": trial.suggest_int("RECOVERY_PRIORITY", 500_000, 5_000_000, step=100_000),
        "RECOVERY_GENERAL": trial.suggest_int("RECOVERY_GENERAL", 50_000, 500_000, step=10_000),
        "SPECIALIZE_AIM_DEVIATION": trial.suggest_int("SPECIALIZE_AIM_DEVIATION", 1000, 5000, step=100),
        "MAINTENANCE_AIM_DEVIATION": trial.suggest_int("MAINTENANCE_AIM_DEVIATION", 500, 4000, step=100),
        "SLOT_ACTIVATION_COUNT": trial.suggest_int("SLOT_ACTIVATION_COUNT", 50, 1000, step=50),
        "LOWER_ANCHOR_ACTIVATION_BONUS": trial.suggest_int("LOWER_ANCHOR_ACTIVATION_BONUS", -1000, -100, step=50),
        "SESSION_FILL_SHORTFALL": trial.suggest_int("SESSION_FILL_SHORTFALL", 1000, 10_000, step=500),
        "SESSION_SET_IMBALANCE": trial.suggest_int("SESSION_SET_IMBALANCE", 500, 5000, step=100),
        "UPPER_BODY_MAINTENANCE_PARITY": trial.suggest_int("UPPER_BODY_MAINTENANCE_PARITY", 500, 5000, step=100),
        "LOWER_BODY_MAINTENANCE_PARITY": trial.suggest_int("LOWER_BODY_MAINTENANCE_PARITY", 500, 5000, step=100),
    }
    
    # Format system property string
    override_str = ",".join(f"{k}:{v}" for k, v in weights.items())
    cmd = [
        "./gradlew",
        ":core:domain-solver:cleanTestDebugUnitTest",
        ":core:domain-solver:testDebugUnitTest",
        "--tests",
        "com.stratum.domain.solver.ProgramSynthesisAcceptanceTest.benchmarkTuningSuite",
        f"-Dstratum.solver.weights={override_str}",
        "-Dstratum.solver.deadline=7000"
    ]
    
    # Delete old loss file to ensure fresh run results
    if os.path.exists(LOSS_PATH):
        os.remove(LOSS_PATH)
        
    print(f"\n--- Trial {trial.number} starting ---")
    print(f"Weights: {weights}")
    
    try:
        # Run test
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if not os.path.exists(LOSS_PATH):
            print(f"Trial {trial.number} failed: Loss file not generated. Gradle output:\n{result.stdout}\n{result.stderr}")
            return 1_000_000.0 # High penalty for failure
            
        with open(LOSS_PATH, "r") as f:
            data = json.load(f)
            loss = float(data.get("loss", 1_000_000.0))
            print(f"Trial {trial.number} finished. Loss: {loss}")
            return loss
            
    except Exception as e:
        print(f"Trial {trial.number} errored: {e}")
        return 1_000_000.0

# The current production defaults (SynthesisObjectiveWeights.kt). Seeded as a fixed
# trial so TPE always has the known-good baseline as an anchor before exploring —
# without this, the first ~10 trials are pure-random and often worse than the defaults
# (the suite is broad enough that defaults are already near a local optimum).
DEFAULT_WEIGHTS = {
    "RECOVERY_PRIORITY": 3_000_000,
    "RECOVERY_GENERAL": 330_000,
    "SPECIALIZE_AIM_DEVIATION": 3_000,
    "MAINTENANCE_AIM_DEVIATION": 4_000,
    "SLOT_ACTIVATION_COUNT": 50,
    "LOWER_ANCHOR_ACTIVATION_BONUS": -250,
    "SESSION_FILL_SHORTFALL": 7_000,
    "SESSION_SET_IMBALANCE": 3_700,
    "UPPER_BODY_MAINTENANCE_PARITY": 3_200,
    "LOWER_BODY_MAINTENANCE_PARITY": 5_000,
}


def run_study(n_trials=10):
    study = optuna.create_study(direction="minimize")
    # Seed with the current production defaults so the optimizer starts from a known-good
    # point. This counts as one of the n_trials.
    study.enqueue_trial(DEFAULT_WEIGHTS)
    study.optimize(objective, n_trials=n_trials)

    print("\n" + "="*40)
    print("Optimization finished!")
    print(f"Best Trial Loss: {study.best_value}")
    print(f"Best Weights: {study.best_params}")
    print(f"Default-weights baseline was one of the trials (seeded).")
    print("="*40)

    # Save best parameters to build/best_weights.json
    build_dir = os.path.dirname(BEST_WEIGHTS_PATH)
    os.makedirs(build_dir, exist_ok=True)
    with open(BEST_WEIGHTS_PATH, "w") as f:
        json.dump({
            "loss": study.best_value,
            "weights": study.best_params
        }, f, indent=2)
    print(f"Best weights configuration saved to {BEST_WEIGHTS_PATH}")

if __name__ == "__main__":
    trials = 10
    if len(sys.argv) > 1:
        try:
            trials = int(sys.argv[1])
        except ValueError:
            pass
    run_study(trials)
