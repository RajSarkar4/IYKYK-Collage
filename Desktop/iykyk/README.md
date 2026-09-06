# IYKYK Video-Based Unique-Person Collage Android Application

An advanced, production-grade Android application built with **Kotlin**, **Jetpack Compose**, **ML Kit Face Detection**, **TensorFlow Lite MobileFaceNet** face embeddings, **Cosine Similarity Agglomerative Clustering**, **Quality-Weighted Identity Centroids**, **Temporal Appearance Tracking**, and **Android Canvas Collage Rendering**.

The application processes arbitrary portrait videos **100% on-device** without any external server or cloud API.

---

## 1. Project Overview

The application accepts any portrait video from the device gallery and automatically performs:

1. **Incremental Frame Extraction**: Samples video frames on-the-fly at configurable target FPS (default 5 FPS) off the main thread with dynamic 1-based progress tracking.
2. **On-Device Face Detection**: Uses Google ML Kit Face Detection to extract face bounding boxes, head pose Euler angles (pitch, yaw, roll), eye openness probabilities, and smiling probabilities.
3. **Motion Blur & Sharpness Scoring**: Calculates Laplacian variance sharpness scores across face crops to identify crisp frames and filter out corrupt/smeared frames.
4. **TensorFlow Lite MobileFaceNet Embeddings**: Inspects dynamic TFLite model input/output tensors, crops face regions with contextual margins, resizes to target input dimensions ($112 \times 112$), normalizes pixel channels ($[-1, 1]$), and executes MobileFaceNet inference to produce L2-normalized 192-dimensional identity embedding vectors.
5. **Quality-Weighted Identity Clustering**: Merges appearance embeddings into unique person identities using cosine similarity ($0.55\text{f}$ threshold) with quality-weighted centroid calculations (`computeWeightedCentroid`) to prevent centroid drift.
6. **Temporal Appearance Tracking**: Calculates continuous appearance segments for each person across time windows using `1200ms` appearance gap thresholding.
7. **Peak Representative Shot Selection**: Evaluates frontality, sharpness, eye openness, smile, face size, and boundary non-clipping to select the single sharpest, highest-quality representative frame for every unique individual.
8. **Canvas Collage Rendering**: Generates a high-resolution $1080 \times 1920$ portrait grid displaying every detected person **exactly once** with appearance count badges and cropped thumbnails.
9. **Gallery Export & Sharing**: Saves generated story collages to device storage via `MediaStore` and enables sharing via standard Android Sharesheet (`Intent.ACTION_SEND`).

---

## 2. Technical Architecture

The codebase follows a modular clean architecture:

```
com.iykyk.collage
├── domain/
│   ├── model/
│   │   ├── FaceObservation.kt      # Lightweight detected face instance data
│   │   ├── Appearance.kt           # Continuous temporal visibility segment
│   │   ├── PersonCluster.kt        # Unique person cluster model
│   │   ├── FaceMatchingConfig.kt   # Central pipeline threshold configuration
│   │   └── ProcessingState.kt      # Sealed UI progress & state hierarchy
│
├── ml/
│   ├── FaceDetector.kt             # ML Kit Face Detection engine
│   ├── FaceEmbedder.kt             # Dynamic TFLite MobileFaceNet model runner
│   ├── EmbeddingSimilarity.kt      # Cosine similarity & weighted centroid math
│   ├── FaceQualityGate.kt          # Multi-tier face quality gate
│   └── FaceQualityScorer.kt        # Laplacian sharpness & representative scoring
│
├── processing/
│   ├── VideoFrameExtractor.kt      # Incremental MediaMetadataRetriever frame sampler
│   ├── PersonIdentityClusterer.kt  # Cosine distance agglomerative identity clusterer
│   ├── MultiPersonAppearanceTracker.kt # Multi-person temporal tracker
│   └── VideoProcessor.kt           # End-to-end pipeline orchestrator & StateFlow runner
│
├── collage/
│   └── CollageGenerator.kt         # Android Canvas 1080x1920 grid renderer
│
├── data/
│   └── storage/
│       └── MediaExporter.kt        # MediaStore export & Sharesheet launcher
│
├── ui/
│   ├── theme/                      # Vibrant dark mode design system (Slate / Indigo / Cyan / Pink)
│   ├── screens/
│   │   ├── HomeScreen.kt           # Video selector & launcher
│   │   ├── ProcessingScreen.kt     # Dynamic, frame-accurate progress ring & linear bar
│   │   ├── ResultsScreen.kt        # Unique person cards list & pipeline metrics
│   │   └── CollageScreen.kt        # High-res 9:16 collage preview, Save & Share
│   └── MainViewModel.kt            # MVVM state coordinator
│
└── MainActivity.kt                 # Jetpack Compose entrypoint & permissions handler
```

---

## 3. Model & Tensor Specifications

### TensorFlow Lite Model Specs
- **Model File**: `app/src/main/assets/mobilefacenet.tflite`
- **Architecture**: MobileFaceNet (Deep Convolutional Neural Network)
- **Input Tensor**:
  - Shape: `[1, 112, 112, 3]`
  - Data Type: `Float32`
  - Pixel Normalization: $(R - 127.5) / 127.5$, $(G - 127.5) / 127.5$, $(B - 127.5) / 127.5$
- **Output Tensor**:
  - Shape: `[1, 192]`
  - Data Type: `Float32`
  - Embedding Dimension: Derived dynamically at runtime via `interpreter.getOutputTensor(0).shape().last()`
- **Postprocessing**: L2 Unit-Norm Vector Normalization: $v_{\text{norm}} = \frac{v}{\|v\|_2}$

---

## 4. Multi-Tier Face Quality Gates

The pipeline enforces two distinct evaluation tiers:

1. **Identity Usability Tier (`isValidForIdentity`)**:
   - Minimum face width/height: $\ge 38\text{px}$.
   - Minimum sharpness: $\ge 1.5\text{f}$ (filters out smeared/corrupt frames).
   - Valid L2-normalized embedding vector (non-empty, finite Float32 values, dimension 192).
2. **Representative Quality Tier (`isValidForRepresentative`)**:
   - **Sharpness**: Laplacian variance score $\ge 5.0\text{f}$ (whip-pan blur threshold = $2.0\text{f}$).
   - **Dimensions**: Face width $\ge 48\text{px}$, Face height $\ge 48\text{px}$.
   - **Boundary Non-Clipping**: Bounding box must not touch image frame boundaries.
   - **Head Pose**: Pitch $\le 40^\circ$, Yaw $\le 50^\circ$.
   - **Eyes Open**: Average eye openness probability $> 0.20\text{f}$ (rejects closed eyes).
   - **Expression**: Frontality, open eyes, smiling probability, and face size composite score.

---

## 5. Build & Setup Instructions

### Environment Requirements
- **Android Studio**: Ladybug / Koala (2024.1+) or newer
- **Gradle**: 8.3+ with Kotlin 1.9.23 / 2.0
- **JDK**: Java 17 or Java 21
- **Minimum SDK**: `26` (Android 8.0 Oreo)
- **Target SDK**: `34` (Android 14)

### Running Locally
1. Clone the repository and open the root folder in Android Studio.
2. Ensure `mobilefacenet.tflite` is present in `app/src/main/assets/`.
3. Connect an Android device or launch an emulator (API 26+).
4. Run `./gradlew assembleDebug` or click **Run 'app'** in Android Studio.

---

## 6. On-Device Processing & Privacy Guarantee

This application operates **100% on-device**:
- **NO Cloud APIs**
- **NO Remote Servers**
- **NO Network Dependency**

All video decoding, frame sampling, ML Kit face detection, MobileFaceNet inference, agglomerative identity clustering, appearance tracking, and collage rendering execute locally on hardware off the main UI thread.

---

## 7. License

Built for the **IYKYK Android Engineering Internship Technical Assignment**.
