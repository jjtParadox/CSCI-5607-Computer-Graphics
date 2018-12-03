cmake_minimum_required(VERSION 3.12)
project(Assignment\ 0.5)

set(CMAKE_CXX_STANDARD 14)

find_package(OpenGL)
find_package(SDL2)
include_directories(${SDL2_INCLUDE_DIRS})

string(STRIP ${SDL2_LIBRARIES} SDL2_LIBRARIES)

add_executable(cube3d Cube3D.cpp glad/glad.c)
target_link_libraries(cube3d ${SDL2_LIBRARIES} ${OPENGL_LIBRARIES} ${CMAKE_DL_LIBS})

add_executable(coloredTriangle ColoredTriangle.cpp glad/glad.c)
target_link_libraries(coloredTriangle ${SDL2_LIBRARIES} ${OPENGL_LIBRARIES} ${CMAKE_DL_LIBS})

add_executable(cubeLit CubeLit.cpp glad/glad.c)
target_link_libraries(cubeLit ${SDL2_LIBRARIES} ${OPENGL_LIBRARIES} ${CMAKE_DL_LIBS})

add_executable(modelLoad ModelLoad.cpp glad/glad.c)
target_link_libraries(modelLoad ${SDL2_LIBRARIES} ${OPENGL_LIBRARIES} ${CMAKE_DL_LIBS})
