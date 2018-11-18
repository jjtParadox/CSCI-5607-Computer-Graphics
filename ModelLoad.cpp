//Rendering 3D Models from File in OpenGL
//(c)Stephen J. Guy, October, 2018
//Compaignion code to: OpenGLCrashCourse.pdf

//This loads and renders a 3D model from a file
//Compiling it requries GLAD (https://glad.dav1d.de/), 
//the SDL2 *development libraries* (https://www.libsdl.org/download-2.0.php),
//and the GLM matrix library (https://glm.g-truc.net/)

//New concepts to understand: File loading, Model storage

#include "glad/glad.h"  //Include order can matter here
#if defined(__APPLE__) || defined(__linux__)
 #include <SDL2/SDL.h>
 #include <SDL2/SDL_opengl.h>
#else
 #include <SDL.h>
 #include <SDL_opengl.h>
#endif
#include <fstream>
using std::ifstream;


bool fullscreen = false;
int screenWidth = 800;
int screenHeight = 600;
void loadShader(GLuint shaderID, const GLchar* shaderSource){
  glShaderSource(shaderID, 1, &shaderSource, NULL); 
  glCompileShader(shaderID);
        
  //Let's double check the shader compiled 
  GLint status; 
  glGetShaderiv(shaderID, GL_COMPILE_STATUS, &status); //Check for errors
  if (!status){
    char buffer[512]; glGetShaderInfoLog(shaderID, 512, NULL, buffer);
    printf("Shader Compile Failed. Info:\n\n%s\n",buffer);
  }
}
#define GLM_FORCE_RADIANS //ensure we are using radians
#include "glm/glm.hpp"
#include "glm/gtc/matrix_transform.hpp"
#include "glm/gtc/type_ptr.hpp"

const GLchar* vertexSource =
"#version 150 core\n"
"in vec3 position;"
"uniform vec3 inColor = vec3(0.f,0.7f,0.f);"
"in vec3 inNormal;"
"const vec3 inlightDir = normalize(vec3(0,0,1));"
"uniform mat4 model;"
"uniform mat4 view;"
"uniform mat4 proj;"

"out vec3 Color;"
"out vec3 normal;"
"out vec3 pos;"
"out vec3 eyePos;"
"out vec3 lightDir;"

"void main() {"
"   Color = inColor;"
"   vec4 pos4 = view * model * vec4(position,1.0);"
"   pos = pos4.xyz/pos4.w;"  //Homogeneous coordinate divide
"   vec4 norm4 = transpose(inverse(view*model)) * vec4(inNormal,0.0);" 
"   normal = norm4.xyz;"  
"   lightDir = (view * vec4(inlightDir,0)).xyz;"  //Transform light into to view space
"   gl_Position = proj * pos4;"
"}";


const GLchar* fragmentSource =
  "#version 150 core\n"
  "in vec3 Color;"
  "in vec3 normal;"
  "in vec3 pos;"
  "in vec3 eyePos;"
  "in vec3 lightDir;"
  "out vec4 outColor;"
  "const float ambient = .3;"
  "void main() {"
  "   vec3 N = normalize(normal);" //Re-normalized the interpolated normals
  "   vec3 diffuseC = Color*max(dot(lightDir,N),0.0);"
  "   vec3 ambC = Color*ambient;" 
  "   vec3 reflectDir = reflect(-lightDir,N);" 
  "   vec3 viewDir = normalize(-pos);"  //We know the eye is at 0,0
  "   float spec = max(dot(reflectDir,viewDir),0.0);"
  "   if (dot(lightDir,N) <= 0.0) spec = 0;"
  "   vec3 specC = vec3(.8,.8,.8)*pow(spec,4);"
  "   outColor = vec4(ambC+diffuseC+specC, 1.0);"
  "}";

int lastTicks;

int charTurn = 0;
int charMov = 0;

float charRot = 0;
float charPosX = 3.0;
float charPosZ = 0;
float charJmpPos = 0;
float charJmpVel = 0;

int mapX, mapY;

bool hasKeyA = false;
bool hasKeyB = false;
bool hasKeyC = false;
bool hasKeyD = false;
bool hasKeyE = false;

float unlockDoorA = 0.0f;
float unlockDoorB = 0.0f;
float unlockDoorC = 0.0f;
float unlockDoorD = 0.0f;
float unlockDoorE = 0.0f;

//int startPosX = 0;
//int startPosY = 0;

int goalPosX = 0;
int goalPosY = 0;

int doorAPosX = -1;
int doorAPosY = -1;
int doorBPosX = -1;
int doorBPosY = -1;
int doorCPosX = -1;
int doorCPosY = -1;
int doorDPosX = -1;
int doorDPosY = -1;
int doorEPosX = -1;
int doorEPosY = -1;

int keyAPosX = -1;
int keyAPosY = -1;
int keyBPosX = -1;
int keyBPosY = -1;
int keyCPosX = -1;
int keyCPosY = -1;
int keyDPosX = -1;
int keyDPosY = -1;
int keyEPosX = -1;
int keyEPosY = -1;


int main(int argc, char *argv[]) {
  ifstream mapFile;
  mapFile.open("map.txt");
  mapFile >> mapX >> mapY;
  bool *walls = new bool[mapX * mapY];
  for (int i = 0; i < mapY; i++) {
      std::string line;
      mapFile >> line;
      for (int j = 0; j < mapX; j++) {
        walls[i * mapX + j] = false;
        switch (line[j]) {
          case 'W':
            walls[i * mapX + j] = true;
            break;
          case 'S':
            charPosX = j;
            charPosZ = i;
            break;
          case 'G':
            goalPosX = j;
            goalPosY = i;
            break;
          case 'a':
            keyAPosX = j;
            keyAPosY = i;
            break;
          case 'b':
            keyBPosX = j;
            keyBPosY = i;
            break;
          case 'c':
            keyCPosX = j;
            keyCPosY = i;
            break;
          case 'd':
            keyDPosX = j;
            keyDPosY = i;
            break;
          case 'e':
            keyEPosX = j;
            keyEPosY = i;
            break;
          case 'A':
            doorAPosX = j;
            doorAPosY = i;
            break;
          case 'B':
            doorBPosX = j;
            doorBPosY = i;
            break;
          case 'C':
            doorCPosX = j;
            doorCPosY = i;
            break;
          case 'D':
            doorDPosX = j;
            doorDPosY = i;
            break;
          case 'E':
            doorEPosX = j;
            doorEPosY = i;
            break;
          default:
            break;
        }
      }
  }

  SDL_Init(SDL_INIT_VIDEO);  //Initialize Graphics (for OpenGL)
    
  //Print the version of SDL we are using 
  SDL_version comp; SDL_version linked;
  SDL_VERSION(&comp); SDL_GetVersion(&linked);
  printf("\nCompiled against SDL version %d.%d.%d\n", comp.major, comp.minor, comp.patch);
  printf("Linked SDL version %d.%d.%d.\n", linked.major, linked.minor, linked.patch);
      
  //Ask SDL to get a recent version of OpenGL (3.2 or greater)
  SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);
  SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3);
  SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 2);
    
  //Create a window (offsetx, offsety, width, height, flags)
  SDL_Window* window = SDL_CreateWindow("My OpenGL Program", 100, 100, 
                                        screenWidth, screenHeight, SDL_WINDOW_OPENGL);
  if (!window){
    printf("Could not create window: %s\n", SDL_GetError()); 
    return EXIT_FAILURE; //Exit as SDL failed 
  }
  float aspect = screenWidth/(float)screenHeight; //aspect ratio needs update on resize
          
  SDL_GLContext context = SDL_GL_CreateContext(window); //Bind OpenGL to the window

  if (gladLoadGLLoader(SDL_GL_GetProcAddress)){
    printf("OpenGL loaded\n");
    printf("Vendor:   %s\n", glGetString(GL_VENDOR));
    printf("Renderer: %s\n", glGetString(GL_RENDERER));
    printf("Version:  %s\n", glGetString(GL_VERSION));
  }
  else {
    printf("ERROR: Failed to initialize OpenGL context.\n");
    return -1;
  }

  GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
  loadShader(vertexShader, vertexSource);
  GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
  loadShader(fragmentShader, fragmentSource);

  //Join the vertex and fragment shaders together into one program
  GLuint shaderProgram = glCreateProgram();
  glAttachShader(shaderProgram, vertexShader);
  glAttachShader(shaderProgram, fragmentShader);
  glBindFragDataLocation(shaderProgram, 0, "outColor"); // set output
  glLinkProgram(shaderProgram); //run the linker

  ifstream teapotFile;
  teapotFile.open("models/teapot.txt");
  int numLines = 0;
  teapotFile >> numLines;
  float* teapotData = new float[numLines];
  for (int i = 0; i < numLines; i++){
          teapotFile >> teapotData[i];
  }
  printf("Mode line count: %d\n",numLines);
  float teapotVerts = numLines/8;

  GLuint teapotVao;
  glGenVertexArrays(1, &teapotVao); //Create a VAO
  glBindVertexArray(teapotVao); //Bind the above created VAO to the current context

  GLuint teapotVbo;
  glGenBuffers(1, &teapotVbo);
  glBindBuffer(GL_ARRAY_BUFFER, teapotVbo);
  glBufferData(GL_ARRAY_BUFFER, numLines*sizeof(float), teapotData, GL_STATIC_DRAW);

  GLint posAttrib = glGetAttribLocation(shaderProgram, "position");
  glVertexAttribPointer(posAttrib, 3, GL_FLOAT, GL_FALSE, 8*sizeof(float), 0);
                            //Attribute, vals/attrib., type, isNormalized, stride, offset
  glEnableVertexAttribArray(posAttrib);
          
  GLint normAttrib = glGetAttribLocation(shaderProgram, "inNormal");
  glVertexAttribPointer(normAttrib, 3, GL_FLOAT, GL_FALSE, 8*sizeof(float), (void*)(5*sizeof(float)));
  glEnableVertexAttribArray(normAttrib);

  ifstream cubeFile;
  cubeFile.open("models/cube.txt");
  numLines = 0;
  cubeFile >> numLines;
  float* cubeData = new float[numLines];
  for (int i = 0; i < numLines; i++){
    cubeFile >> cubeData[i];
  }
  printf("Mode line count: %d\n",numLines);
  float cubeVerts = numLines/8;

  GLuint cubeVao;
  glGenVertexArrays(1, &cubeVao); //Create a VAO
  glBindVertexArray(cubeVao); //Bind the above created VAO to the current context

  GLuint cubeVbo;
  glGenBuffers(1, &cubeVbo);
  glBindBuffer(GL_ARRAY_BUFFER, cubeVbo);
  glBufferData(GL_ARRAY_BUFFER, numLines*sizeof(float), cubeData, GL_STATIC_DRAW);

  posAttrib = glGetAttribLocation(shaderProgram, "position");
  glVertexAttribPointer(posAttrib, 3, GL_FLOAT, GL_FALSE, 8*sizeof(float), 0);
  //Attribute, vals/attrib., type, isNormalized, stride, offset
  glEnableVertexAttribArray(posAttrib);

  normAttrib = glGetAttribLocation(shaderProgram, "inNormal");
  glVertexAttribPointer(normAttrib, 3, GL_FLOAT, GL_FALSE, 8*sizeof(float), (void*)(5*sizeof(float)));
  glEnableVertexAttribArray(normAttrib);

  ifstream sphereFile;
  sphereFile.open("models/sphere.txt");
  numLines = 0;
  sphereFile >> numLines;
  float* sphereData = new float[numLines];
  for (int i = 0; i < numLines; i++){
    sphereFile >> sphereData[i];
  }
  printf("Mode line count: %d\n",numLines);
  float sphereVerts = numLines/8;

  GLuint sphereVao;
  glGenVertexArrays(1, &sphereVao); //Create a VAO
  glBindVertexArray(sphereVao); //Bind the above created VAO to the current context

  GLuint sphereVbo;
  glGenBuffers(1, &sphereVbo);
  glBindBuffer(GL_ARRAY_BUFFER, sphereVbo);
  glBufferData(GL_ARRAY_BUFFER, numLines*sizeof(float), sphereData, GL_STATIC_DRAW);

  posAttrib = glGetAttribLocation(shaderProgram, "position");
  glVertexAttribPointer(posAttrib, 3, GL_FLOAT, GL_FALSE, 8*sizeof(float), 0);
  //Attribute, vals/attrib., type, isNormalized, stride, offset
  glEnableVertexAttribArray(posAttrib);

  normAttrib = glGetAttribLocation(shaderProgram, "inNormal");
  glVertexAttribPointer(normAttrib, 3, GL_FLOAT, GL_FALSE, 8*sizeof(float), (void*)(5*sizeof(float)));
  glEnableVertexAttribArray(normAttrib);
  glEnable(GL_DEPTH_TEST);
 
  SDL_Event windowEvent;
  bool quit = false;
  lastTicks = SDL_GetTicks();
  while (!quit){
    while (SDL_PollEvent(&windowEvent)){
      if (windowEvent.type == SDL_QUIT) quit = true; //Exit Game Loop
      if (windowEvent.type == SDL_KEYUP && windowEvent.key.keysym.sym == SDLK_ESCAPE) 
        quit = true; //Exit Game Loop
      if (windowEvent.type == SDL_KEYUP && windowEvent.key.keysym.sym == SDLK_f){ 
        fullscreen = !fullscreen; 
        SDL_SetWindowFullscreen(window, fullscreen ? SDL_WINDOW_FULLSCREEN : 0);
      }

      if (windowEvent.type == SDL_KEYDOWN) {
        if (windowEvent.key.keysym.sym == SDLK_LEFT) {
          charTurn = -1;
        }
        else if (windowEvent.key.keysym.sym == SDLK_RIGHT) {
          charTurn = 1;
        }
        if (windowEvent.key.keysym.sym == SDLK_UP) {
          charMov = 1;
        } else if (windowEvent.key.keysym.sym == SDLK_DOWN) {
          charMov = -1;
        }
        if (windowEvent.key.keysym.sym == SDLK_SPACE && charJmpPos == 0.0) {
            charJmpVel = 5;
        }
      }
      if (windowEvent.type == SDL_KEYUP) {
        if (windowEvent.key.keysym.sym == SDLK_LEFT || windowEvent.key.keysym.sym == SDLK_RIGHT) {
          charTurn = 0;
        }
        if (windowEvent.key.keysym.sym == SDLK_UP || windowEvent.key.keysym.sym == SDLK_DOWN) {
          charMov = 0;
        }
      }
    }
    // Clear the screen to default color
    glClearColor(.2f, 0.4f, 0.8f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    int deltaTicks = SDL_GetTicks() - lastTicks;
    lastTicks += deltaTicks;
    float time = deltaTicks/1000.0f;

    charRot -= time * charTurn * 1;
    charPosX -= time * charMov * cos((double) charRot) * 1;
    charPosZ -= time * charMov * sin((double) charRot) * 1;

    charJmpPos += time * charJmpVel;
    charJmpVel -= time * 20;
    if (charJmpPos < 0) charJmpPos = 0;
    if (charJmpVel < -100) charJmpVel = -100;

    if (unlockDoorA > 0 && unlockDoorA < 5) {
      unlockDoorA += time * 2;
    }
    if (unlockDoorB > 0 && unlockDoorB < 5) {
      unlockDoorB += time * 2;
    }
    if (unlockDoorC > 0 && unlockDoorC < 5) {
      unlockDoorC += time * 2;
    }
    if (unlockDoorD > 0 && unlockDoorD < 5) {
      unlockDoorD += time * 2;
    }
    if (unlockDoorE > 0 && unlockDoorE < 5) {
      unlockDoorE += time * 2;
    }

    glm::mat4 model;
//    model = glm::rotate(model,time * 3.14f/2,glm::vec3(0.0f, 1.0f, 1.0f));
//    model = glm::rotate(model,time * 3.14f/4,glm::vec3(1.0f, 0.0f, 0.0f));
    GLint uniModel = glGetUniformLocation(shaderProgram, "model");
    glUniformMatrix4fv(uniModel, 1, GL_FALSE, glm::value_ptr(model));

    glm::vec3 greenColor(0.0f, 1.0f, 0.0f);
    GLint uniColor = glGetUniformLocation(shaderProgram, "inColor");
    glUniform3fv(uniColor, 1, glm::value_ptr(greenColor));

    glm::mat4 view;
    view = glm::translate(view,glm::vec3(charPosX, charPosZ, charJmpPos));
    view = glm::rotate(view,charRot,glm::vec3(0.0f, 0.0f, 1.0f));
    view = glm::rotate(view,(float)M_PI_2,glm::vec3(0.0f, 1.0f, 0.0f));
    view = glm::rotate(view,(float)M_PI_2,glm::vec3(0.0f, 0.0f, 1.0f));

    view = glm::inverse(view);

    GLint uniView = glGetUniformLocation(shaderProgram, "view");
    glUniformMatrix4fv(uniView, 1, GL_FALSE, glm::value_ptr(view));

    glm::mat4 proj = glm::perspective(3.14f/4, aspect, 0.1f, 10.0f);
                                      //FOV, aspect ratio, near, far
    GLint uniProj = glGetUniformLocation(shaderProgram, "proj");
    glUniformMatrix4fv(uniProj, 1, GL_FALSE, glm::value_ptr(proj));

    glUseProgram(shaderProgram);
    glBindVertexArray(teapotVao);  //Bind the VAO for the shaders we are using
    glDrawArrays(GL_TRIANGLES, 0, teapotVerts); //Number of vertices

    // Draw goal
    glBindVertexArray(sphereVao);
    glm::mat4 sphereModel;
    sphereModel = glm::translate(sphereModel,glm::vec3(goalPosX, goalPosY, 0.0));
    glUniformMatrix4fv(uniModel, 1, GL_FALSE, glm::value_ptr(sphereModel));
    glm::vec3 yellowColor(1.0f, 1.0f, 0.0f);
    glUniform3fv(uniColor, 1, glm::value_ptr(yellowColor));
    glDrawArrays(GL_TRIANGLES, 0, sphereVerts);

    // Draw walls and floors
    glBindVertexArray(cubeVao);
    glUniform3fv(uniColor, 1, glm::value_ptr(greenColor));
    for (int i = 0; i < mapX; i++) {
      for (int j = 0; j < mapY; j++) {
        int yPos = -1;
        if (walls[j * mapX + i]) {
          yPos = 0;
        }
        glm::mat4 cubeModel;
        cubeModel = glm::translate(cubeModel,glm::vec3(i, j, yPos));
        glUniformMatrix4fv(uniModel, 1, GL_FALSE, glm::value_ptr(cubeModel));
        glDrawArrays(GL_TRIANGLES, 0, cubeVerts);
      }
    }

    SDL_GL_SwapWindow(window); //Double buffering
  }
  
  glDeleteProgram(shaderProgram);
  glDeleteShader(fragmentShader);
  glDeleteShader(vertexShader);
  glDeleteBuffers(1, &teapotVbo);
  glDeleteVertexArrays(1, &teapotVao);
  glDeleteBuffers(1, &cubeVbo);
  glDeleteVertexArrays(1, &cubeVao);
  glDeleteBuffers(1, &sphereVbo);
  glDeleteVertexArrays(1, &sphereVao);
  SDL_GL_DeleteContext(context);
  SDL_Quit();

  return 0;
}

