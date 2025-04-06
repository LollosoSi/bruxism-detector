import numpy as np
import pandas as pd
from sklearn.svm import SVC
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import cross_val_score

# Carica i dati
clenching = pd.read_csv("clenching.csv", header=None).values
non_clenching = pd.read_csv("non_clenching.csv", header=None).values

# Crea dataset con etichette
X = np.vstack((clenching, non_clenching))
y = np.array([1] * len(clenching) + [0] * len(non_clenching))

# Normalizza i dati
scaler = StandardScaler()
X = scaler.fit_transform(X)

# Confronto tra diversi kernel
kernels = ['linear', 'poly', 'rbf', 'sigmoid']
best_kernel = None
best_score = 0
print("\n")
for k in kernels:
    model = SVC(kernel=k)
    scores = cross_val_score(model, X, y, cv=5)
    mean_score = scores.mean()
    print(f"Kernel: {k}, Accuracy: {mean_score:.3f}")
    
    if mean_score > best_score:
        best_score = mean_score
        best_kernel = k

print(f"Best kernel: {best_kernel} with accuracy {best_score:.3f}")

print("\nUsing linear because that one is implemented in the arduino sketch.")
best_kernel="linear"

# Addestra il modello con il miglior kernel
model = SVC(kernel=best_kernel)
model.fit(X, y)

# Se il kernel è lineare, estrai i pesi e il bias per Arduino
if best_kernel == 'linear':
    weights = model.coef_[0]  # I pesi sono in model.coef_
    bias = model.intercept_[0]  # Il bias è in model.intercept_

    # Salva pesi + bias su file, senza virgola finale
    with open("svm_weights.txt", "w") as f:
        f.write(", ".join(f"{w:.8f}" for w in np.append(weights, bias)))

    # Calcolo dei punteggi di classificazione
    def compute_classification_scores(X, weights, bias):
        return np.dot(X, weights) + bias

    # Calcola i punteggi per entrambe le classi
    clenching_scores = compute_classification_scores(clenching, weights, bias)
    non_clenching_scores = compute_classification_scores(non_clenching, weights, bias)

    # Calcola statistiche
    mean_clenching = np.mean(clenching_scores)
    min_clenching = np.min(clenching_scores)
    max_clenching = np.max(clenching_scores)

    mean_non_clenching = np.mean(non_clenching_scores)
    min_non_clenching = np.min(non_clenching_scores)
    max_non_clenching = np.max(non_clenching_scores)
        
    # Calcola deviazione standard per entrambe le classi
    std_clenching = np.std(clenching_scores)
    std_non_clenching = np.std(non_clenching_scores)
    
    # Calcola threshold suggerito spostato verso clenching
    suggested_threshold = mean_clenching - abs(mean_non_clenching - mean_clenching) * 0.3
    
    # Stampa riepilogo completo
    print("\n// Classification score stats:")
    print(f"// Clenching     -> min: {min_clenching:.6f}, max: {max_clenching:.6f}, mean: {mean_clenching:.6f}, deviation: {std_clenching:.6f}")
    print(f"// Non-Clenching -> min: {min_non_clenching:.6f}, max: {max_non_clenching:.6f}, mean: {mean_non_clenching:.6f}, deviation: {std_non_clenching:.6f}")
    
    print(f"\n// Halfway threshold:")
    print(f"classification_threshold = {(max_non_clenching + min_clenching) / 2:.0f};")
    
    print("\n// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -")
    print("\n\n// Copy this and replace in your sketch\n")
    
    # Stampa i pesi nel formato per Arduino
    print("static const float weights[] = {", end=" ")
    print(", ".join(f"{w:.8f}" for w in weights), end=" ")
    print("};")

    # Stampa il bias nel formato per Arduino
    print(f"static const float bias = {bias:.16f};")
    
    print(f"\n// Suggested threshold:")
    print(f"static const int classification_threshold = {suggested_threshold:.0f};")
    print("\n// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -")

else:
    support_vectors = model.support_vectors_
    dual_coefs = model.dual_coef_
    bias = model.intercept_

    # Stampa il codice C++ con i support vectors e coefficienti duali
    print("\n// Parametri SVM")
    print(f"const int numSV = {len(support_vectors)}; // Numero di support vectors")
    print(f"const int numFeatures = {support_vectors.shape[1]}; // Numero di features")
    print("double supportVectors[numSV][numFeatures] = {")
    for sv in support_vectors:
        print(f"    {{" + ", ".join([f"{x:.6f}" for x in sv]) + "}},")
    print("};")
    print("double dualCoefs[numSV] = {")
    for coef in dual_coefs[0]:
        print(f"    {coef:.6f},")
    print("};")
    print(f"double bias = {bias[0]:.6f}; // Carica il bias da Python\n")
