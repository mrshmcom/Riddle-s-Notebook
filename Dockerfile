FROM ubuntu:22.04

# Set non-interactive mode for apt
ENV DEBIAN_FRONTEND=noninteractive

# Install core dependencies
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set Java home
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin

# ── Android SDK Setup ─────────────────────────────────────────────
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0

RUN mkdir -p $ANDROID_HOME/cmdline-tools

# Download Android command-line tools
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q commandlinetools-linux-*.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm commandlinetools-linux-*.zip

# Accept SDK licenses
RUN yes | sdkmanager --licenses > /dev/null 2>&1

# Install required SDK packages
RUN sdkmanager \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "extras;android;m2repository" \
    "extras;google;m2repository" \
    "extras;google;instantapps"

# ── Gradle Setup ──────────────────────────────────────────────────
ENV GRADLE_VERSION=8.7
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip -q gradle-${GRADLE_VERSION}-bin.zip -d /opt \
    && rm gradle-${GRADLE_VERSION}-bin.zip
ENV PATH=$PATH:/opt/gradle-${GRADLE_VERSION}/bin
ENV GRADLE_USER_HOME=/root/.gradle

# ── Workspace ─────────────────────────────────────────────────────
WORKDIR /app

# Copy project files
COPY . .

# Generate Gradle wrapper
RUN gradle wrapper --gradle-version ${GRADLE_VERSION}

# Build the APK
RUN ./gradlew assembleDebug --no-daemon --stacktrace

# ── Output ────────────────────────────────────────────────────────
# The APK will be at: /app/app/build/outputs/apk/debug/app-debug.apk
# Extract it with:
#   docker run --rm -v $(pwd)/output:/output <image> cp /app/app/build/outputs/apk/debug/app-debug.apk /output/

CMD ["bash", "-c", "cp /app/app/build/outputs/apk/debug/app-debug.apk /output/ 2>/dev/null || echo 'APK built. Use docker run with volume mount to extract it.'"]
