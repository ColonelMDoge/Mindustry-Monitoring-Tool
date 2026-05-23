# Mindustry-Monitoring-Tool
>  ### A lightweight Java desktop tool for coordinating Mindustry multiplayer sessions. Tracks who's online, automatically assigns a host, syncs save data via Git, and shuts down the game when the host disconnects. Includes a Node.js presence server with API key authentication and heartbeat-based session management.
> Created by Francis Lin (NerdyNerd090) and Alex Zhou (ColonelMDoge) as a way to reliably play Mindustry Campaign as a headless-esque server.
<p align="center">
  <img width="225" height="225" alt="Icon" src="https://github.com/user-attachments/assets/f576c6a1-107d-4fad-951b-91e23b988427" />
</p>

## Prerequisites:
1. Mindustry v157.4
2. A working cloud server to host the server file
3. NodeJS via `npm install`
4. Java 17 to run both the monitoring tool and Mindustry v157.4
## Installation:
1. Clone the repository by using `git clone https://github.com/ColonelMDoge/Mindustry-Monitoring-Tool`
## Usage:
3. Run `Win + R` and enter `%appdata%`. Locate the Mindustry data folder. Copy it it and paste it into the `cData` folder in the cloned repo.
4. Transfer and run the `server.js` file on the cloud server by executing `API_KEY=[YOUR_KEY_HERE] PORT=[YOUR_PORT_HERE] node server.js`
     - The API_KEY acts as the password
     - Ensure the port is opened on your cloud server.
5. Run the `mindustryLauncher.bat` file. This will automatically download the latest save file and upload it once the host of the game leaves.
     - The webhook URL is your your cloud IP and the associated port number
     - The game path is the Mindustry executable file.
6. Have fun!
## Features:
1. Node.js GET and POST functionality
2. Saves user-inputs to allow auto-start at a future point in time
3. Log section to track possible errors and the obtain the host's IP address
4. Clean, user-friendly UI

## Future Add-ons:
1. Linux launcher file support.
