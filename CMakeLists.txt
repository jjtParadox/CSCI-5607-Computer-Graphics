cmake_minimum_required(VERSION 3.12)
project(Assignment\ 4)

set(CMAKE_CXX_STANDARD 14)

find_package(OpenGL)
find_package(SDL2)
include_directories(${SDL2_INCLUDE_DIRS})

string(STRIP ${SDL2_LIBRARIES} SDL2_LIBRARIES)

add_executable(assignment4 Assignment4.cpp glad/glad.c)
target_link_libraries(assignment4 ${SDL2_LIBRARIES} ${OPENGL_LIBRARIES} ${CMAKE_DL_LIBS})
