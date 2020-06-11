import cv2
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.patches as patches
import flirimageextractor
import os
def normalizeImage(thermal):  #Change thermal Image from celcius (°C) to unit8 (255)
  minThermal = np.min(np.array(thermal))
  maxThermal = np.max(np.array(thermal))
  diffThermal = maxThermal - minThermal
  thermal = (255.0/diffThermal)*(thermal - minThermal)
  thermal = thermal.astype(np.uint8)
  return thermal
def splitThermalRGB(imgPath):
  flir = flirimageextractor.FlirImageExtractor()
  flir.process_image(imgPath)
  rgb = flir.extract_embedded_image()
  thermal = flir.extract_thermal_image()
  shape = np.array(rgb).shape
  scaleFactor = 0.81
  thermal = normalizeImage(np.array(cv2.resize(thermal, (int(shape[1]*scaleFactor),int(shape[0]*scaleFactor)))))
  if rgb.shape[0] == 1080:
    thermal = cv2.warpAffine(thermal, np.float32([[1,0,180],[0,1,85]]), (shape[1],shape[0]))
  else:
    thermal = cv2.warpAffine(thermal, np.float32([[1,0,120],[0,1,185]]), (shape[1],shape[0]))
  return thermal, rgb
def processImageGamma(path, gamma=1.0): #Fix RGB Image with Gamma Adjustment
	thermal, rgb = splitThermalRGB(path)
	invGamma = 1.0 / gamma
	table = np.array([((i / 255.0) ** invGamma) * 255 for i in np.arange(0, 256)]).astype("uint8")
	return cv2.LUT(rgb, table), thermal
def drawBoxes(fullPath, imgPath):
  textFileName = fullPath+'labels/'+imgPath[:-4]+".txt"
  label = open(textFileName, 'r')
  boxes = label.readlines()
  img = processImageGamma(fullPath+imgPath, 2)
  shape = img.shape
  imgHeight = shape[0]
  imgWidth = shape[1]
  fig,ax = plt.subplots(1)
  ax.imshow(img)
  if len(boxes) > 0:
    for box in boxes:
        arr = box.split(' ')
        boxWidth = float(arr[3]) * imgWidth
        boxHeight = float(arr[4]) * imgHeight
        x = (float(arr[1])) * imgWidth
        y = (float(arr[2])) * imgHeight
        pointX = x - boxWidth/2
        pointY = y - boxHeight/2
        rect = patches.Rectangle((pointX,pointY),boxWidth,boxHeight,linewidth=1,edgecolor='r',facecolor='none')
        ax.add_patch(rect)
  plt.show()
def drawBoxesRGB(imgPath):
  textFileName = 'NewLabels/'+imgPath[:-4]+".txt"
  label = open(textFileName, 'r')
  boxes = label.readlines()
  img = plt.imread('RGB/'+imgPath)
  imgHeight, imgWidth, _ = img.shape
  thermal = plt.imread('Thermal/'+imgPath)
  fig,ax = plt.subplots(1)
  r1, g1, b1 = cv2.split(img)
  _, _, thermal = cv2.split(thermal)
  newImage = cv2.merge((thermal, g1, b1))
  ax.imshow(newImage)
  if len(boxes) > 0:
    for box in boxes:
        arr = box.split(' ')
        boxWidth = float(arr[3]) * imgWidth
        boxHeight = float(arr[4]) * imgHeight
        x = (float(arr[1])) * imgWidth
        y = (float(arr[2])) * imgHeight
        pointX = x - boxWidth/2
        pointY = y - boxHeight/2
        rect = patches.Rectangle((pointX,pointY),boxWidth,boxHeight,linewidth=1,edgecolor='r',facecolor='none')
        ax.add_patch(rect)
  plt.show()
def fixImage(rgb, thermal, fullPath, imgPath, newPathRGB, newPathThermal, newPathLabels):
  rgb = np.rot90(rgb)
  thermal = np.rot90(thermal)
  rgb = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
  thermal = cv2.cvtColor(thermal, cv2.COLOR_RGB2BGR)
  cv2.imwrite(fullPath+newPathRGB+imgPath,rgb)
  cv2.imwrite(fullPath+newPathThermal+imgPath,thermal)
  #Fix Labels
  labelName = fullPath+'labels/'+imgPath[:-4]+".txt"
  label = open(labelName, 'r')
  boxes = label.readlines()
  newBoxes = ""
  if len(boxes) > 0:
    for box in boxes:
        arr = box.split(' ')
        boxWidth = str(float(arr[4]))
        boxHeight = str(float(arr[3]))+"\n"
        x = str(float(arr[2]))
        y = str(1 - float(arr[1]))
        arr[1] = x
        arr[2] = y
        arr[3] = boxWidth
        arr[4] = boxHeight
        newBoxes = newBoxes + ' '.join(arr)
    label.close()
    label = open(fullPath + newPathLabels + imgPath[:-4]+".txt", 'w')
    label.write(newBoxes)
    label.close()

# Takes every image and extracts its RGB and thermal parts and saves each
# Also fixes and horizontal images by rotating them and fixing their labels
fullPath = './test Data/'
newPathRGB = 'RGB/'
newPathThermal = 'Thermal/'
newPathLabels = 'NewLabels/'
for imgPath in os.listdir(fullPath):
  if imgPath.endswith('.jpg') and not os.path.isfile(newPathRGB+imgPath):
    rgb, thermal = processImageGamma(fullPath+imgPath, 2)
    if rgb.shape[0] == 1440:
      rgb = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
      thermal = cv2.cvtColor(thermal, cv2.COLOR_RGB2BGR)
      cv2.imwrite(fullPath + newPathRGB+imgPath,rgb)
      cv2.imwrite(fullPath + newPathThermal+imgPath,thermal)
      # Save label in new labels folder
      labelName = fullPath+'labels/'+imgPath[:-4]+".txt"
      label = open(labelName, 'r')
      newBoxes = label.readlines()
      label.close()
      label = open(fullPath + newPathLabels + imgPath[:-4]+".txt", 'w')
      label.write(''.join(newBoxes))
      label.close()
    else:
      fixImage(rgb, thermal, fullPath, imgPath, newPathRGB, newPathThermal, newPathLabels)

# Draws RGB and Thermal together with the bounding boxes
for path in os.listdir():
  if path.endswith('.jpg'):
    print(path)
    drawBoxesRGB(path)
