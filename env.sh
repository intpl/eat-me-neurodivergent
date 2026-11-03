# Toolchain for this repo, kept outside the repo so a container rebuild
# only means reinstalling Gradle/JDK/SDK under /home/b/dev.
TC=/home/b/dev/android-toolchain
export JAVA_HOME="$TC/jdk17"
export ANDROID_HOME="$TC/sdk"
export ANDROID_SDK_ROOT="$TC/sdk"
export GRADLE_USER_HOME="$TC/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$TC/gradle-8.11.1/bin:$PATH"
