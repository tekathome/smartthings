
metadata {
    definition (name: "ZigBee Switch Power test", namespace: "tayfun", author: "tayfun") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"
        capability "Polling"

        // Smartenit 30A Switch
        // Cluster 0000 - Basic
        // Cluster 0003 - Identify
        // Cluster 0004 - Groups
        // Cluster 0005 - Scenes
        // Cluster 0006 - On/Off
        // Cluster 0008 - Level Control
        // Cluster 0019 - OTA Upgrade
        // Cluster 0702 - Metering
        fingerprint endpointId: "01", profileId: "0104", inClusters: "0000,0003,0006,0004,0005,0008", outClusters: "0019"
        fingerprint endpointId: "02", profileId: "0104", inClusters: "0702,0006"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", 
        		width: 6, height: 4, canChangeIcon: true) {
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", 
                	icon:"st.switches.switch.on", backgroundColor:"#79b821",
                    nextState:"turningOff"
                    
                attributeState "off", label:'${name}', action:"switch.on", 
                	icon:"st.switches.switch.off", backgroundColor:"#ffffff", 
                    nextState:"turningOn"
                    
                attributeState "turningOn", label:'${name}', action:"switch.off", 
                	icon:"st.switches.switch.on", backgroundColor:"#79b821", 
                    nextState:"turningOff"
                    
                attributeState "turningOff", label:'${name}', action:"switch.on", 
                	icon:"st.switches.switch.off", backgroundColor:"#ffffff", 
                    nextState:"turningOn"
            }
            tileAttribute ("power", key: "SECONDARY_CONTROL") {
                attributeState "power", label:'${currentValue}'
            }
        }
        
        standardTile("refresh", "device.switch", inactiveLabel: false, 
        		decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", 
            icon:"st.secondary.refresh"
        }

        standardTile("configure", "device.switch", 
        		decoration: "flat", width: 2, height: 2) {
			state "configure", label:"", action:"configuration.configure", 
            icon:"st.secondary.configure"
        }

        preferences {
            input "switchAutoOff", "boolean", title: "Auto Off", defaultValue: false
        }

		attribute "attrDivisor", "number"		// Divisor
        attribute "attrMultiplier", "number"	// Multiplier
        attribute "attrPower", "number"			// Current Power
        attribute "attrDelivered", "number"		// Power delivered
        attribute "attrUnit", "number"			// Unit of Measure
        attribute "attrPendingOff", "number"

        main "switch"

        details(["switch", "power", "refresh", "configure" ])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    int mult = device.currentValue("attrMultiplier") // Multiplier as reported by device
	int div = device.currentValue("attrDivisor") // Divisor as reported by device
    int power = device.currentValue("attrPower") // Current Power as reported by device
    int delivered = device.currentValue("attrDelivered") // Total power delivered as reported by device
    int unit = device.currentValue("attrUnit") // Unit of Measure as reported by device

	//log.info "Current mult=$mult, div=$div, pwr=$pwr, delivered=$delivered"

	//log.debug "description is $description"

	if (description?.startsWith("read attr -")) {
        def descMap = parseDescriptionAsMap(description)
		//log.debug "cluster $descMap.cluster attrId $descMap.attrId encoding $descMap.encoding value $descMap.value"
		switch(descMap.cluster) {
        case "0702":
			switch (descMap.attrId) {
			case "0300": // Unit of Measure
				//log.debug "Found 702/0300 Unit of Measure"
                unit = Integer.parseInt(descMap.value, 16)
               	//log.info "Unit of Measure : $unit"
               	sendEvent(name:"attrUnit", value:"$unit") 
	           	break;

			case "0301": // Multiplier
				//log.debug "Found 702/0301 Multiplier"
                mult = Integer.parseInt(descMap.value, 16)
                //log.info "Multiplier : $mult"
               	sendEvent(name:"attrMultiplier", value:"$mult") 
	           	break;

 			case "0302": // Divisor
				//log.debug "Found 702/0302 Divisor"
                div = Integer.parseInt(descMap.value, 16)
                //log.info "Divisor : $div"
	           	sendEvent(name:"attrDivisor", value:"$div") 
                div = device.currentValue("attrDivisor")
	           	break;

            case "0000": // Power Delivered
				log.debug "Found 702/0000 Power Delivered"
                delivered = Integer.parseInt(descMap.value, 16)
				sendEvent(name:"attrDelivered", value:"$delivered") 
                log.debug "702/0000 Div=$div, mult=$mult, power=$power, del=$delivered"

                if (div != 0) {
                	def name = "power"
                	Double delkw = delivered * mult / div
                    Double curkw = power * mult / div
					def kw = sprintf("Total %.1f kWh / %.2f kW", delkw, curkw)
                    log.debug "sending event name=$name, value=$kw"
                	sendEvent(name: "power", value: "$kw")
				}
            	break;

			case "0400": // Power
				log.debug "Found 702/0400 Power"
               	power = Integer.parseInt(descMap.value, 16)
                sendEvent(name:"attrPower", value:"$power")
                log.debug "702/0400 Div=$div, mult=$mult, power=$power del=$delivered"

                if (div != 0) {
                	def name = "power"
                	Double delkw = delivered * mult / div
                    Double curkw = power * mult / div
					def kw = sprintf("Total %.1f kWh / %.2f kW", delkw, curkw)
                    log.debug "sending event name=$name, value=$kw"
                	sendEvent(name: "power", value: "$kw")
				}
            	break;

             default:
             	log.debug "Ignoring - Cluster: $descMap.cluster Attribute: $descMap.attrId"
                break
	         } // end switch attrId
             break
             
		default:
        	log.debug "Ignoring - Cluster: $descMap.cluster"
            break
		} // end switch clusterID
	}

	else if (description?.startsWith("on/off: ")) {
        //log.debug "Processing on/off description : $description"
        if (description?.endsWith(" 1")) {
        	def name = "switch"
          	def value = "on"
			log.debug "Sending $name $value event"
            sendEvent(name: "$name", value: "$value")
		} else if (description?.endsWith(" 0")) {
        	def name = "switch"
			def value = "off"
            log.debug "Sending $name $value event"
            sendEvent(name: "$name", value: "$value")
		} else {
			log.debug "Ignored description : $description" 
        }
	}

	else if (description?.startsWith("catchall: 0104 0006 01 01 0140 00") ||
		description?.startsWith("catchall: 0104 0006 02 01 0140 00")) {
		if (description?.endsWith(" 0100") || description?.endsWith(" 1001")) {
        	def name = "switch"
            def value = "on"
			sendEvent(name: "$name", value: "$value")
            log.debug "Sending $name $value event"
		} else if (description?.endsWith(" 0000") || description?.endsWith(" 1000")) {
        	def name = "switch"
            def value = "off"
			sendEvent(name: "$name", value: "$value")
            log.debug "Sending $name $value event"
        }
	}
    
    else if (description?.startsWith("catchall: C25D 0001 03")) {
        	// Known mystery ... ignore it
    } else {
			//log.debug "Ignoring description - $description"
 	}
}

def off() {
    [
    	// Endpoint 1, Cluster 0006, Value Off
	    "st cmd 0x${device.deviceNetworkId} 1 0006 0 {}", "delay 200",
        
		// Endpoint - 2, Cluster - 0006, Value - Off
		// "st cmd 0x${device.deviceNetworkId} 2 0006 0 {}", "delay 200",

		// Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0000", "delay 200",

	    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0400", "delay 200",
	]
}

def on() {
    [
    	// Endpoint 1, Cluster 0006, Value On
    	"st cmd 0x${device.deviceNetworkId} 1 0006 1 {}", "delay 200",
        
        // Endpoint 2, Cluster 0006, Value  On
    	// "st cmd 0x${device.deviceNetworkId} 2 0006 1 {}", "delay 200"

		// Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0000", "delay 200",

	    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0400", "delay 200",
    ]
}

def poll() {
    [
        // Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
        "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0000", "delay 200",
	    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0400", "delay 200",
    ]
}

def refresh() {
	[
 		// Switch status, EndPoint 1, Cluster = 0x0006 - On/Off(Switch), Attribute = 0x0000
	    "st rattr 0x${device.deviceNetworkId} 1 0x0006 0x0000", "delay 200",

 		// Switch status, EndPoint 2, Cluster = 0x0006 - On/Off(Switch), Attribute = 0x0000
	    "st rattr 0x${device.deviceNetworkId} 2 0x0006 0x0000", "delay 200",

	    // Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0000", "delay 200",

	    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0400", "delay 200",

	    // Unit of Measure, EndPoint 2, Cluster 702 Attribute 0300
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0300", "delay 200",

	    // Multiplier, EndPoint 2, Cluster 702 Attribute 0301
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0301", "delay 200",

	    // Divisor, EndPoint 2, Cluster 0702 Atribute 0302
	    "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0302", "delay 200",
	]    
}

// zigbee.onOffRefresh() - 0104 0006 01 01 0140 00 43B5 00 00 0000 01 01 0000001000
// zigbee.onOffConfig()  - 0104 0006 01 01 0140 00 43B5 00 00 0000 07 01 8C000000
// zigbee.simpleMeteringPowerConfig() - None
// zigbee.simpleMeteringPowerRefresh() - None
// zigbee.electricMeasurementPowerConfig() - None
// zigbee.electricMeasurementPowerRefresh() - None
def configure() {
    log.debug "Configuring Reporting and Bindings."
	sendEvent(name:"attrMultiplier", value:"1") 
	sendEvent(name:"attrDivisor", value:"100000") 
	sendEvent(name:"attrUnit", value:"0") 
	sendEvent(name:"attrPower", value:"-1") 
	sendEvent(name:"attrDelivered", value:"0") 

	// Endpoint 1, Cluster 0000 - Basic
   	"zdo bind 0x${device.deviceNetworkId} 1 1 0x0000 {${device.zigbeeId}} {}"

    // Endpoint 1, Cluster 0006 - On/Off
    "zdo bind 0x${device.deviceNetworkId} 1 1 0x0006 {${device.zigbeeId}} {}"
    "zdo bind 0x${device.deviceNetworkId} 2 1 0x0006 {${device.zigbeeId}} {}"

    // Endpoint 1, Cluster 702 - Metering
    "zdo bind 0x${device.deviceNetworkId} 2 1 0x0702 {${device.zigbeeId}} {}"
}

def parseDescriptionAsMap(description) {
    (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}
