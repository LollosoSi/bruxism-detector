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

for k in kernels:
    model = SVC(kernel=k)
    scores = cross_val_score(model, X, y, cv=5)
    mean_score = scores.mean()
    print(f"Kernel: {k}, Accuracy: {mean_score:.3f}")
    
    if mean_score > best_score:
        best_score = mean_score
        best_kernel = k

print(f"Miglior kernel: {best_kernel} con accuracy {best_score:.3f}")

print("Uso comunque linear perche si")
best_kernel="linear"

# Addestra il modello con il miglior kernel
model = SVC(kernel=best_kernel)
model.fit(X, y)

# Se il kernel è lineare, estrai i pesi e il bias per Arduino
if best_kernel == 'linear':
    weights = model.coef_[0]  # I pesi sono in model.coef_
    bias = model.intercept_[0]  # Il bias è in model.intercept_

    # Stampa i pesi nel formato per Arduino
    print("static const float weights[] = {", end=" ")
    for weight in weights:
        print(f"{weight:.8f},", end=" ")
    print("};")

    # Stampa il bias nel formato per Arduino
    print(f"static const float bias = {bias:.16f};")
    np.savetxt("svm_weights.txt", np.append(weights, bias), delimiter=",")
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
