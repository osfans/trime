project(TRIME)
cmake_minimum_required (VERSION 3.3)

add_subdirectory(jni)

if(ANDROID)
# make [trime|apk]
add_custom_target(trime ALL
  COMMAND ant release
  DEPENDS rime_jni)
add_custom_target(apk DEPENDS trime)

# make install
install(CODE "execute_process(COMMAND ant install)")
endif(ANDROID)
