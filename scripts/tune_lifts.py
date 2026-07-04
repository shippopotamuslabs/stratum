import optuna
import subprocess
import json
import os
import sys

LOSS_PATH = "core/domain/build/lift_selection_loss.json"
BEST_WEIGHTS_PATH = "core/domain/build/best_lift_weights.json"

def objective(trial):
    # Propose weights to tune
    weights = {
        "PROVEN_TIER_WEIGHT": trial.suggest_float("PROVEN_TIER_WEIGHT", 0.0, 1.0),
        "RECENCY_WEIGHT": trial.suggest_float("RECENCY_WEIGHT", 0.0, 1.0),
        "EFFECTIVENESS_WEIGHT": trial.suggest_float("EFFECTIVENESS_WEIGHT", 0.0, 1.0),
        "PROGRESS_TREND_WEIGHT": trial.suggest_float("PROGRESS_TREND_WEIGHT", 0.0, 1.0),
        "EVIDENCE_WEIGHT": trial.suggest_float("EVIDENCE_WEIGHT", 0.0, 1.0),
        "HAS_INSIGHTS_WEIGHT": trial.suggest_float("HAS_INSIGHTS_WEIGHT", 0.0, 1.0),
        "MAINSTREAM_WEIGHT": trial.suggest_float("MAINSTREAM_WEIGHT", 0.0, 1.0),
        
        "ANCHOR_MAINSTREAM_BONUS": trial.suggest_float("ANCHOR_MAINSTREAM_BONUS", 0.0, 1.0),
        "ANCHOR_BARBELL_BONUS": trial.suggest_float("ANCHOR_BARBELL_BONUS", 0.0, 1.0),
        "ANCHOR_BRAND_PENALTY": trial.suggest_float("ANCHOR_BRAND_PENALTY", -1.0, 0.0),
        "ANCHOR_MACHINE_PENALTY": trial.suggest_float("ANCHOR_MACHINE_PENALTY", -1.0, 0.0),
        "ISOLATION_CABLE_MACHINE_BONUS": trial.suggest_float("ISOLATION_CABLE_MACHINE_BONUS", 0.0, 1.0),
        
        "LENGTH_BIAS_BONUS": trial.suggest_float("LENGTH_BIAS_BONUS", 0.0, 1.0),
        
        "CYCLE_VARIETY_USED_PENALTY": trial.suggest_float("CYCLE_VARIETY_USED_PENALTY", -1.0, 0.0),
        "CYCLE_VARIETY_FRESH_BONUS": trial.suggest_float("CYCLE_VARIETY_FRESH_BONUS", 0.0, 1.0),
        "SYSTEMIC_FATIGUE_PENALTY": trial.suggest_float("SYSTEMIC_FATIGUE_PENALTY", -1.0, 0.0),
    }
    
    # Format system property string
    override_str = ",".join(f"{k}:{v:.4f}" for k, v in weights.items())
    cmd = [
        "./gradlew",
        ":core:domain:cleanTest",
        ":core:domain:test",
        "--tests",
        "com.stratum.domain.program.generation.selection.LiftSelectionTuningTest",
        f"-Dstratum.lift.weights={override_str}"
    ]
    
    if os.path.exists(LOSS_PATH):
        os.remove(LOSS_PATH)
        
    print(f"\n--- Trial {trial.number} starting ---")
    print(f"Weights: {weights}")
    
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if not os.path.exists(LOSS_PATH):
            print(f"Trial {trial.number} failed: Loss file not generated. Gradle output:\n{result.stdout}\n{result.stderr}")
            return 10000.0
            
        with open(LOSS_PATH, "r") as f:
            data = json.load(f)
            loss = float(data.get("loss", 10000.0))
            print(f"Trial {trial.number} finished. Loss: {loss}")
            return loss
            
    except Exception as e:
        print(f"Trial {trial.number} errored: {e}")
        return 10000.0

def run_study(n_trials=20):
    study = optuna.create_study(direction="minimize")
    study.optimize(objective, n_trials=n_trials)
    
    print("\n" + "="*40)
    print("Optimization finished!")
    print(f"Best Trial Loss: {study.best_value}")
    print(f"Best Weights: {study.best_params}")
    print("="*40)
    
    build_dir = os.path.dirname(BEST_WEIGHTS_PATH)
    os.makedirs(build_dir, exist_ok=True)
    with open(BEST_WEIGHTS_PATH, "w") as f:
        json.dump({
            "loss": study.best_value,
            "weights": study.best_params
        }, f, indent=2)
    print(f"Best weights configuration saved to {BEST_WEIGHTS_PATH}")

if __name__ == "__main__":
    trials = 20
    if len(sys.argv) > 1:
        try:
            trials = int(sys.argv[1])
        except ValueError:
            pass
    run_study(trials)
