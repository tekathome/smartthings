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
        }

    tiles {
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°', unit:'${temperatureScale}', backgroundColors:[
                [value: 70, color: "#153591"],
                [value: 75, color: "#1e9cbb"],
                [value: 80, color: "#90d2a7"],
                [value: 85, color: "#44b621"],
                [value: 90, color: "#f1d801"],
                [value: 95, color: "#d04e00"],
                [value: 100, color: "#bc2323"]
            ])
        }
        controlTile("heatSliderControl", "device.heatingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false, range:"(70..106)") {
            state "setHeatingSetpoint", action:"thermostat.setHeatingSetpoint", backgroundColor:"#e86d13"
        }
        valueTile("heatingSetpoint", "device.heatingSetpoint", inactiveLabel: false, decoration: "flat") {
            state "heat", label:'${currentValue}°', unit:'${temperatureScale}', backgroundColor:"#ffffff"
        }

		standardTile("refresh", "device.temperature", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
		main "temperature"
		details(["temperature", "heatSliderControl", "heatingSetpoint", "refresh", "configure"])
    }
   
    simulator {
        // TODO: define status and reply messages here
    }
}

// parse events into attributes
def parse(description) {

    def msg = parseLanMessage(description)

    log.debug "Parsing '${description}'"

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
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
		log.debug "setHeatingSetpoint({$degreesInteger} ${temperatureScale})"
		sendEvent("name": "heatingSetpoint", "value": degreesInteger, "unit": temperatureScale)

		def celsius = (getTemperatureScale() == "C") ? degreesInteger : (fahrenheitToCelsius(degreesInteger) as Double).round(2)
		// "st wattr 0x${device.deviceNetworkId} 1 0x201 0x12 0x29 {" + hex(celsius * 100) + "}"
	}
}
def refresh() {
    def setPoint = device.currentValue("heatingSetpoint")

	log.debug "refresh called ${setPoint}"

    def degreesInteger = Math.round(setPoint)
    def temperatureScale = getTemperatureScale()

    log.debug "${degreesInteger} ${temperatureScale}"

    sendEvent("name": "temperature", "value": degreesInteger, "unit": temperatureScale)   
    // sendEvent("name": "heat", "value": degreesInteger, "unit": temperatureScale)

	log.debug "refresh ended"
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

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
