//Colored Triangle Rendering in OpenGL
//(c)Stephen J. Guy, October, 2018
//Compaignion code to: OpenGLCrashCourse.pdf

//This code produces a colored triangle using OpenGL.
//Compiling it requries GLAD (https://glad.dav1d.de/) and 
//the SDL2 *development libraries* (https://www.libsdl.org/download-2.0.php)

//Concepts to understand: Loading SDL and GLAD, Moving data to VBOs, 
//   Setting up mappings VAO, Compling Shaders, GLSL basics,
//   rendering VBOs with glDrawArrays(), normalized device coordinates

#include "glad/glad.h"  //Include order can matter here
#if defined(__APPLE__) || defined(__linux__)
 #include <SDL2/SDL.h>
 #include <SDL2/SDL_opengl.h>
#else
 #include <SDL.h>
 #include <SDL_opengl.h>
#endif

const GLchar* vertexSource =
"#version 150 core\n"
"in vec2 position;"
"in vec3 inColor;"
"out vec3 Color;"
"void main() {"
"   Color = inColor;"
"   gl_Position = vec4(position, 0.0, 1.0);"
"}";

const GLchar* fragmentSource =
    "#version 150 core\n"
    "in vec3 Color;"
    "out vec4 outColor;"
    "void main() {"
    "   outColor = vec4(Color, 1.0);"  //(Red, Green, Blue, Alpha)
    "}";

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

int main(int argc, char *argv[]) {
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

  GLfloat vertices[] = { 
    0.0f,  0.5f, 1.0f, 0.0f, 0.0f, // Vertex 1: postion = (0,.5) color = Red
    0.5f, -0.5f, 0.0f, 1.0f, 0.0f, // Vertex 2: postion = (.5,-.5) color = Green
    -0.5f, -0.5f, 0.0f, 0.0f, 1.0f}; // Vertex 3: postion = (-.5,-.5) color = Blue

  GLuint vbo;
  glGenBuffers(1, &vbo);  //Create 1 buffer called vbo
  glBindBuffer(GL_ARRAY_BUFFER, vbo); //(Only one buffer can be bound at a time)
  glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW); 

  GLuint vao;
  glGenVertexArrays(1, &vao); //Create a VAO
  glBindVertexArray(vao); //Bind the above created VAO to the current context
  GLint posAttrib = glGetAttribLocation(shaderProgram, "position");
  glVertexAttribPointer(posAttrib, 2, GL_FLOAT, GL_FALSE, 5*sizeof(float), 0);
  //(above params: Attribute, vals/attrib., type, isNormalized, stride, offset)
  glEnableVertexAttribArray(posAttrib); //Mark the attribute's location as valid
          
  GLint colAttrib = glGetAttribLocation(shaderProgram, "inColor");
  glVertexAttribPointer(colAttrib, 3, GL_FLOAT, GL_FALSE, 
                        5*sizeof(float), (void*)(2*sizeof(float)));
  glEnableVertexAttribArray(colAttrib);     

  glBindVertexArray(0); //Unbind the VAO so we don't accidentally modify it

  SDL_Event windowEvent;
  bool quit = false;
  while (!quit){
    while (SDL_PollEvent(&windowEvent)){
      if (windowEvent.type == SDL_QUIT) quit = true; //Exit Game Loop
      if (windowEvent.type == SDL_KEYUP && windowEvent.key.keysym.sym == SDLK_ESCAPE) 
        quit = true; //Exit Game Loop
      if (windowEvent.type == SDL_KEYUP && windowEvent.key.keysym.sym == SDLK_q)
        quit = true; //Exit Game Loop
      if (windowEvent.type == SDL_KEYUP && windowEvent.key.keysym.sym == SDLK_f){ 
        fullscreen = !fullscreen; 
        SDL_SetWindowFullscreen(window, fullscreen ? SDL_WINDOW_FULLSCREEN : 0);
      }  
    }

    glClearColor(.2f, 0.4f, 0.8f, 1.0f);  // Clear the screen to blue
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(shaderProgram); //Set the active shader program          
    glBindVertexArray(vao);  //Bind the VAO for the shader(s) we are using
    glDrawArrays(GL_TRIANGLES, 0, 3);
          
    SDL_GL_SwapWindow(window); //Double buffering
  }

  glDeleteProgram(shaderProgram);
  glDeleteShader(fragmentShader);
  glDeleteShader(vertexShader);
  glDeleteBuffers(1, &vbo);
  glDeleteVertexArrays(1, &vao);
  SDL_GL_DeleteContext(context);
  SDL_Quit();

  return 0;
}
