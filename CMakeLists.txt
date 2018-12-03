cmake_minimum_required(VERSION 3.12)
project(clion)

set(CMAKE_CXX_STANDARD 14)

find_package(OpenGL)
find_package(SDL2)
include_directories(square ${SDL2_INCLUDE_DIRS})

#add_executable(clion main.cpp)
add_executable(square Square.cpp glad/glad.c)
target_link_libraries(square ${SDL2_LIBRARIES} ${OPENGL_LIBRARIES} ${CMAKE_DL_LIBS})
