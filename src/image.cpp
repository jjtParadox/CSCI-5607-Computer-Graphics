#include "image.h"
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <float.h>

/**
 * Image
 **/
Image::Image (int width_, int height_){

    assert(width_ > 0);
    assert(height_ > 0);

    width           = width_;
    height          = height_;
    num_pixels      = width * height;
    sampling_method = IMAGE_SAMPLING_POINT;
    
    data.raw = new uint8_t[num_pixels*4];
	int b = 0; //which byte to write to
	for (int j = 0; j < height; j++){
		for (int i = 0; i < width; i++){
			data.raw[b++] = 0;
			data.raw[b++] = 0;
			data.raw[b++] = 0;
			data.raw[b++] = 0;
		}
	}

    assert(data.raw != NULL);
}

Image::Image (const Image& src){
	
	width           = src.width;
    height          = src.height;
    num_pixels      = width * height;
    sampling_method = IMAGE_SAMPLING_POINT;
    
    data.raw = new uint8_t[num_pixels*4];
    
    //memcpy(data.raw, src.data.raw, num_pixels);
    *data.raw = *src.data.raw;
}

Image::Image (char* fname){

	int numComponents; //(e.g., Y, YA, RGB, or RGBA)
	data.raw = stbi_load(fname, &width, &height, &numComponents, 4);
	
	if (data.raw == NULL){
		printf("Error loading image: %s", fname);
		exit(-1);
	}
	

	num_pixels = width * height;
	sampling_method = IMAGE_SAMPLING_POINT;
	
}

Image::~Image (){
    delete data.raw;
    data.raw = NULL;
}

void Image::Write(char* fname){
	
	int lastc = strlen(fname);

	switch (fname[lastc-1]){
	   case 'g': //jpeg (or jpg) or png
	     if (fname[lastc-2] == 'p' || fname[lastc-2] == 'e') //jpeg or jpg
	        stbi_write_jpg(fname, width, height, 4, data.raw, 95);  //95% jpeg quality
	     else //png
	        stbi_write_png(fname, width, height, 4, data.raw, width*4);
	     break;
	   case 'a': //tga (targa)
	     stbi_write_tga(fname, width, height, 4, data.raw);
	     break;
	   case 'p': //bmp
	   default:
	     stbi_write_bmp(fname, width, height, 4, data.raw);
	}
}

void Image::AddNoise (double factor)
{
	int x, y;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p = GetPixel(x, y);
			Pixel random = PixelRandom() * factor;
			GetPixel(x, y) = p + random;
		}
	}
}

void Image::Brighten (double factor)
{
	int x,y;
	for (x = 0 ; x < Width() ; x++)
	{
		for (y = 0 ; y < Height() ; y++)
		{
			Pixel p = GetPixel(x, y);
			Pixel scaled_p = p*factor;
			GetPixel(x,y) = scaled_p;
		}
	}
}


void Image::ChangeContrast (double factor)
{
	factor = factor - 1;
	int x, y;
	double averageLuminance = 0;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p = GetPixel(x, y);
			Component luminance = p.Luminance();
			averageLuminance += luminance;
		}
	}
	averageLuminance /= (Width() * Height());
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p = GetPixel(x, y);
			double r = p.r + (p.r - averageLuminance)*factor;
			double g = p.g + (p.g - averageLuminance)*factor;
			double b = p.b + (p.b - averageLuminance)*factor;
			p.SetClamp(r, g, b);
			GetPixel(x, y) = p;
		}
	}
}


void Image::ChangeSaturation(double factor)
{
	factor = factor - 1;
    int x, y;
    for (x = 0; x < Width(); x++) {
        for (y = 0; y < Height(); y++) {
            Pixel p = GetPixel(x, y);
            Component luminance = p.Luminance();
            double r = p.r + (p.r - luminance)*factor;
			double g = p.g + (p.g - luminance)*factor;
			double b = p.b + (p.b - luminance)*factor;
			p.SetClamp(r, g, b);
            GetPixel(x, y) = p;
        }
    }
}


Image* Image::Crop(int x, int y, int w, int h)
{
	Image *newImg = new Image(w, h);
	int cx, cy;
	for (cx = x; cx < w + x; cx++) {
		for (cy = y; cy < h + y; cy++) {
			newImg->SetPixel(cx - x, cy - y, GetPixel(cx, cy));
		}
	}
	return newImg;
}


void Image::ExtractChannel(int channel)
{
	int x, y;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p = GetPixel(x, y);
			switch (channel) {
				case 0:
					p.g = 0;
					p.b = 0;
					break;
				case 1:
					p.r = 0;
					p.b = 0;
					break;
				case 2:
					p.r = 0;
					p.g = 0;
					break;
				default:
					break;
			}
			GetPixel(x, y) = p;
		}
	}
}


void Image::Quantize (int nbits)
{
	int x, y;
	double step = 255.0/(pow(2, nbits)-1);
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p = GetPixel(x, y);
			int r = (int) (step * (int) floor((double) p.r/step + 0.5));
			int g = (int) (step * (int) floor((double) p.g/step + 0.5));
			int b = (int) (step * (int) floor((double) p.b/step + 0.5));
			p.SetClamp(r, g, b);
			GetPixel(x, y) = p;
		}
	}
}

void Image::RandomDither (int nbits)
{
	int x, y;
	double step = 255.0/(pow(2, nbits)-1);
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p = GetPixel(x, y);
			double rnd = (rand() % (int) (step) - (step/2))/255.0;
			int r = (int) (step * (int) floor((double) p.r/step + rnd + 0.5));
			int g = (int) (step * (int) floor((double) p.g/step + rnd + 0.5));
			int b = (int) (step * (int) floor((double) p.b/step + rnd + 0.5));
			p.SetClamp(r, g, b);
			GetPixel(x, y) = p;
		}
	}
}


static int Bayer4[4][4] =
{
    {15,  7, 13,  5},
    { 3, 11,  1,  9},
    {12,  4, 14,  6},
    { 0,  8,  2, 10}
};


void Image::OrderedDither(int nbits)
{
	/* WORK HERE */
}

/* Error-diffusion parameters */
const double
    ALPHA = 7.0 / 16.0,
    BETA  = 3.0 / 16.0,
    GAMMA = 5.0 / 16.0,
    DELTA = 1.0 / 16.0;

void Image::FloydSteinbergDither(int nbits)
{
	int x, y;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel p, quant, er;
			p = GetPixel(x, y);
			quant = PixelQuant(p, nbits);
			int r = p.r - quant.r;
			int g = p.g - quant.g;
			int b = p.b - quant.b;
			if (x+1 < Width()) {
			    er = GetPixel(x+1, y);
			    er.SetClamp(er.r + r * ALPHA, er.g + g * ALPHA, er.b + b * ALPHA);
				GetPixel(x+1, y) = er;
			}
			if (y+1 < Height()) {
				if (x-1 > 0) {
					er = GetPixel(x-1, y+1);
					er.SetClamp(er.r + r * BETA, er.g + g * BETA, er.b + b * BETA);
					GetPixel(x-1, y+1) = er;
				}
				er = GetPixel(x, y+1);
				er.SetClamp(er.r + r * GAMMA, er.g + g * GAMMA, er.b + b * GAMMA);
				GetPixel(x, y+1) = er;
				if (x+1 < Width()) {
					er = GetPixel(x+1, y+1);
					er.SetClamp(er.r + r * DELTA, er.g + g * DELTA, er.b + b * DELTA);
					GetPixel(x+1, y+1) = er;
				}
			}
			GetPixel(x, y) = quant;
		}
	}
}

void Image::Blur(int n)
{
	int x, y;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			Pixel convolvedX = Pixel(0, 0, 0);
			int filterX, filterY;
			for (filterX = x - 3*n; filterX <= x + 3*n; filterX++) {
				int tmpX = abs(filterX);
				if (tmpX >= Width()) {
					tmpX -= 2*(tmpX - Width()) + 1;
				}
				convolvedX = convolvedX + GetPixel(tmpX, y) * (
						1.0/sqrt(2*M_PI*pow((double) n, 2)) *
						pow(M_E, -pow(filterX - x, 2)/(2*pow(n, 2)))
						);
			}
			Pixel convolvedY = Pixel(0, 0, 0);
			for (filterY = y - 3*n; filterY <= y + 3*n; filterY++) {
				int tmpY = abs(filterY);
				if (tmpY >= Height()) {
					tmpY -= 2*(tmpY - Height()) + 1;
				}
				convolvedY = convolvedY + GetPixel(x, tmpY) * (
						1.0/sqrt(2*M_PI*pow((double) n, 2)) *
						pow(M_E, -pow(filterY - y, 2)/(2*pow(n, 2)))
				);
			}
			GetPixel(x, y) = convolvedX * 0.5 + convolvedY * 0.5;
		}
	}
}

void Image::Sharpen(int n)
{
	Image *blurred = Crop(0, 0, Width(), Height());
	blurred->Blur(n);
	int x, y;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
			GetPixel(x, y) = PixelLerp(GetPixel(x, y), blurred->GetPixel(x, y), -1);
		}
	}
}

static int EdgeM[3][3] = {
		{-1, -1, -1},
		{-1,  8, -1},
		{-1, -1, -1}
};

void Image::EdgeDetect()
{
    Image* oldPic = Crop(0, 0, Width(), Height());
	int x, y;
	for (x = 0; x < Width(); x++) {
		for (y = 0; y < Height(); y++) {
		    int r = 0;
			int g = 0;
			int b = 0;
			int filterX, filterY;
			for (filterX = x - 1; filterX <= x + 1; filterX++) {
				for (filterY = y - 1; filterY <= y + 1; filterY++) {
					int tmpX = abs(filterX);
					int tmpY = abs(filterY);
					if (tmpX >= Width()) {
						tmpX -= 2 * (tmpX - Width()) + 1;
					}
					if (tmpY >= Height()) {
						tmpY -= 2 * (tmpY - Height()) + 1;
					}
					Pixel p = oldPic->GetPixel(tmpX, tmpY);
					r += p.r * (EdgeM[filterX - x + 1][filterY - y + 1]);
					g += p.g * (EdgeM[filterX - x + 1][filterY - y + 1]);
					b += p.b * (EdgeM[filterX - x + 1][filterY - y + 1]);
				}
			}
			GetPixel(x, y).SetClamp(r, g, b);
		}
	}
}

Image* Image::Scale(double sx, double sy)
{
    Image* newImg = new Image((int) (sx*Width()), (int) (sy*Height()));
    int x, y;
    for (x = 0; x < newImg->Width(); x++) {
    	for (y = 0; y < newImg->Height(); y++) {
    	    double u = (double) x / sx;
			double v = (double) y / sy;
			newImg->GetPixel(x, y) = Sample(u, v);
    	}
    }
	return newImg;
}

Image* Image::Rotate(double angle)
{
	int p1x, p1y, p2x, p2y, p3x, p3y;
	p1x = (int) (cos(angle) * Width());
	p1y = (int) (sin(angle) * Width());
	p2x = (int) (cos(angle + atan2(Height(), Width())) * sqrt(Width() * Width() + Height() * Height()));
	p2y = (int) (sin(angle + atan2(Height(), Width())) * sqrt(Width() * Width() + Height() * Height()));
	p3x = (int) (cos(angle + M_PI/2) * Height());
	p3y = (int) (sin(angle + M_PI/2) * Height());
	int maxX, maxY, minX, minY;
	maxX = (int) fmax(0, fmax(p1x, fmax(p2x, p3x)));
	maxY = (int) fmax(0, fmax(p1y, fmax(p2y, p3y)));
	minX = (int) fmin(0, fmin(p1x, fmin(p2x, p3x)));
	minY = (int) fmin(0, fmin(p1y, fmin(p2y, p3y)));
	int sizeX = maxX - minX;
	int sizeY = maxY - minY;
    Image* newImg = new Image(sizeX, sizeY);
    int x, y;
    for (x = minX; x < maxX; x++) {
        for (y = minY; y < maxY; y++) {
            double u = (double) x * cos(-angle) - (double) y * sin(-angle);
            double v = (double) x * sin(-angle) + (double) y * cos(-angle);
            newImg->GetPixel(x - minX, y - minY) = Sample(u, v);
        }
    }
    return newImg;
}

void Image::Fun()
{
    Image* oldImg = Crop(0, 0, Width(), Height());
    int x, y;
    for (x = 0; x < Width(); x++) {
    	for (y = 0; y < Height(); y++) {
    		double u = x + sin((double) x / Width() * 100) * 20;
			double v = y + sin((double) y / Width() * 100) * 20;
    	    GetPixel(x, y) = oldImg->Sample(u, v);
    	}
    }
}

/**
 * Image Sample
 **/
void Image::SetSamplingMethod(int method)
{
    assert((method >= 0) && (method < IMAGE_N_SAMPLING_METHODS));
    sampling_method = method;
}


Pixel Image::Sample(double u, double v) {
	if (!ValidCoord((int) u, (int) v)) {
		Pixel px = Pixel(0, 0, 0, 0);
		return px;
	}
	Pixel p, pX, pY;
	int radius;
	int x, y;
    switch(sampling_method) {
    	case IMAGE_SAMPLING_POINT:
    	    p = GetPixel((int) u, (int) v);
    		break;
    	case IMAGE_SAMPLING_BILINEAR:
    		pX = Pixel(0, 0, 0);
			pY = Pixel(0, 0, 0);
    		radius = 5;
    	    for (x = -radius; x <= radius; x++) {
				int tmpX = (int) fabs(u + x);
				if (tmpX >= Width()) {
					tmpX -= 2*(tmpX - Width()) + 1;
				}
				pX = pX + GetPixel(tmpX, (int) v) * ((1.0 - fabs((double) x / (double) radius)) / radius);
    	    }
			for (y = -radius; y <= radius; y++) {
				int tmpY = (int) fabs(v + y);
				if (tmpY >= Height()) {
					tmpY -= 2*(tmpY - Height()) + 1;
				}
				pY = pY + GetPixel((int) u, tmpY) * ((1.0 - fabs((double) y / (double) radius)) / radius);
			}
			p = pX * 0.5 + pY * 0.5;
    		break;
    	case IMAGE_SAMPLING_GAUSSIAN:
			pX = Pixel(0, 0, 0);
			pY = Pixel(0, 0, 0);
			radius = 2;
			for (x = -3*radius; x <= 3*radius; x++) {
				int tmpX = (int) fabs(u + x);
				if (tmpX >= Width()) {
					tmpX -= 2*(tmpX - Width()) + 1;
				}
				pX = pX + GetPixel(tmpX, (int) v) * (
						1.0/sqrt(2*M_PI*pow((double) radius, 2)) *
						pow(M_E, -pow(x, 2)/(2*pow(radius, 2)))
				);
			}
			for (y = -3*radius; y <= 3*radius; y++) {
				int tmpY = (int) fabs(v + y);
				if (tmpY >= Height()) {
					tmpY -= 2*(tmpY - Height()) + 1;
				}
				pY = pY + GetPixel((int) u, tmpY) * (
						1.0/sqrt(2*M_PI*pow((double) radius, 2)) *
						pow(M_E, -pow(y, 2)/(2*pow(radius, 2)))
				);
			}
			p = pX * 0.5 + pY * 0.5;
    		break;
		default:break;
	}
	return p;
}