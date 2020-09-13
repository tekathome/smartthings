/**
 *  Generic UPnP Device
 *
 *  Copyright 2016 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition (name: "Jacuzzi J-LX", namespace: "smartthings", author: "SmartThings") {
        capability "Actuator"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"

        command "raiseSetpoint"
        command "lowerSetpoint"
    }

    tiles {
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°', unit:'${temperatureScale}', backgroundColors:[
                [value: 75, color: "#153591"],
                [value: 80, color: "#1e9cbb"],
                [value: 85, color: "#90d2a7"],
                [value: 90, color: "#44b621"],
                [value: 95, color: "#f1d801"],
                [value: 100, color: "#d04e00"],
                [value: 105, color: "#bc2323"]
            ])
        }

        valueTile("heatingSetpoint", "device.heatingSetpoint", inactiveLabel: false, decoration: "flat") {
            state "heat", label:'${currentValue}°', unit:'${temperatureScale}', backgroundColor:"#ffffff"
        }

        standardTile("upButtonControl", "device.thermostatSetpoint", inactiveLabel: false, decoration: "flat") {
			state "setpoint", action:"raiseSetpoint", icon:"st.thermostat.thermostat-up"
		}

        standardTile("downButtonControl", "device.thermostatSetpoint", inactiveLabel: false, decoration: "flat") {
			state "setpoint", action:"lowerSetpoint", icon:"st.thermostat.thermostat-down"
		}

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

        preferences {
            input name: "minTemp", type: "number", title: "Min Temp °F", 
                description: "Enter Lowest Temp", required: true, 
                defaultValue: 70, range: "75..106"
            input name: "maxTemp", type: "number", title: "Max Temp °F", 
                description: "Enter Highest Temp", required: true, 
                defaultValue: 106, range: "75..106"
        }

		main "temperature"
		details(["temperature", "upButtonControl", "heatingSetpoint", "refresh", "configure", "downButtonControl" ])

    }
   
    simulator {
        // TODO: define status and reply messages here
    }
}

// parse events into attributes
def parse(description) {

    def msg = parseLanMessage(description)

    // log.debug "Parsing '${description}'"

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
    def ip = msg.ip
    def port = msg.port

    sync(ip, port)

    log.debug "Parse ip ${ip}, port ${port}"
}

def getTemperature(value) {
	if (value != null) {
		def celsius = Integer.parseInt(value, 16) / 100
		if (getTemperatureScale() == "C") {
			return celsius
		} else {
			return Math.round(celsiusToFahrenheit(celsius))
		}
	}
}

def setHeatingSetpoint(degrees) {
	if (degrees != null) {
		def temperatureScale = getTemperatureScale()
		def degreesInteger = Math.round(degrees)

        log.debug "/setHeatingSetpoint?set=${degreesInteger}&scale=${temperatureScale}"

		log.debug "setHeatingSetpoint({$degreesInteger} ${temperatureScale})"
		sendEvent("name": "heatingSetpoint", "value": degreesInteger, "unit": temperatureScale)


        def result = new physicalgraph.device.HubAction (
            method: "POST",
            path: "/setHeatingSetpoint?set=${degreesInteger}&scale=${temperatureScale}",
            headers: [
                HOST: getHostAddress()
            ]
        )
	}
}

def raiseSetpoint() {
    def setPoint = device.currentValue("heatingSetpoint")
    if (setPoint < maxTemp) {
        log.debug "In raiseSetpoint() $maxTemp $minTemp"
        setHeatingSetpoint(setPoint + 1)
    }
}

def lowerSetpoint() {
    def setPoint = device.currentValue("heatingSetpoint")
   if (setPoint > minTemp) {
       log.debug "In lowerSetpoint() $maxTemp $minTemp"
       setHeatingSetpoint(setPoint - 1)
   }
}

def refresh() {
    def setPoint = device.currentValue("heatingSetpoint")
    def degreesInteger = Math.round(setPoint)
    def temperatureScale = getTemperatureScale()
    
    log.debug "refresh called ${setPoint} ${degreesInteger} ${temperatureScale}"

    sendEvent("name": "temperature", "value": degreesInteger, "unit": temperatureScale)   

	log.debug "refresh ended"
    subscribeAction("/path/of/event")
}

def sync(ip, port) {
        def existingIp = getDataValue("ip")
        def existingPort = getDataValue("port")
        if (ip && ip != existingIp) {
                updateDataValue("ip", ip)
        }
        if (port && port != existingPort) {
                updateDataValue("port", port)
        }
}

// gets the address of the Hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }
    port = "1f90"
    log.debug "Using IP: ${convertHexToIP(ip)} and port: ${convertHexToInt(port)} for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private subscribeAction(path, callbackPath="") {
    log.trace "subscribe($path, $callbackPath)"
    def address = getCallBackAddress()
    def ip = getHostAddress()

    def result = new physicalgraph.device.HubAction(
        method: "SUBSCRIBE",
        path: path,
        headers: [
            HOST: getHostAddress(),
            CALLBACK: "<http://${address}/notify$callbackPath>",
            NT: "upnp:event",
            TIMEOUT: "Second-28800"
        ]
    )

    log.trace "SUBSCRIBE $path"

    return result
}
