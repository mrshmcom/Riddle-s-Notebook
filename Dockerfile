# Use the existing builder image which has Android SDK + Gradle cache
# This avoids needing to access dl.google.com (blocked in some regions)
FROM riddles-diary-builder:latest AS base

# Fix DNS for any remaining network requests (Maven Central, Gradle Plugin Portal etc.)
COPY fix-dns.sh /usr/local/bin/fix-dns

# Copy updated source files
COPY . /app/

# Rebuild the APK using cached dependencies
WORKDIR /app
RUN sh /usr/local/bin/fix-dns && \
    ./gradlew assembleDebug --no-daemon --stacktrace

CMD ["bash", "-c", "cp /app/app/build/outputs/apk/debug/app-debug.apk /output/ 2>/dev/null || echo 'APK built'"]
