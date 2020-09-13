/**
 *  Copyright 2019 SmartThings
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
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "Zigbee Metering Plug Fresh", namespace: "smartthings", author: "SmartThings", ocfDeviceType: "oic.d.smartplug", mnmn: "SmartThings",  vid: "generic-switch-power-energy") {
        capability "Energy Meter"
        capability "Power Meter"
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        capability "Health Check"
        capability "Sensor"
        capability "Configuration"

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04, 0702, FC82", outClusters: "0003, 000A, 0019", manufacturer: "LDS", model: "ZB-ONOFFPlug-D0000",  deviceJoinName: "Outlet" //Smart Plug
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04, 0702, FC82", outClusters: "0003, 000A, 0019", manufacturer: "LDS", model: "ZB-ONOFFPlug-D0005",  deviceJoinName: "Outlet" //Smart Plug
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0B04", outClusters: "0003", manufacturer: "REXENSE", model: "HY0105", deviceJoinName: "HONYAR Outlet" //HONYAR Smart Outlet (USB)
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0B04", outClusters: "0003", manufacturer: "REXENSE", model: "HY0104", deviceJoinName: "HONYAR Outlet" //HONYAR Smart Outlet
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0009, 0702, 0B04", outClusters: "0003, 0019", manufacturer: "HEIMAN", model: "E_Socket", deviceJoinName: "HEIMAN Outlet" //HEIMAN Smart Outlet
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04, 0702, FC82", outClusters: "0003, 000A, 0019", manufacturer: "sengled", model: "E1C-NB7",  deviceJoinName: "Sengled Outlet" //Sengled Smart Plug with Energy Tracker
        fingerprint manufacturer: "DAWON_DNS", model: "PM-B430-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug PM-B430-ZB (10A), raw description: 01 0104 0051 01 07 0000, 0004, 0003, 0006, 0019, 0702, 0B04 07 0000, 0004, 0003, 0006, 0019, 0702, 0B04
        fingerprint manufacturer: "DAWON_DNS", model: "PM-B530-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug PM-B530-ZB (16A), raw description: 01 0104 0051 01 07 0000, 0004, 0003, 0006, 0019, 0702, 0B04 07 0000, 0004, 0003, 0006, 0019, 0702, 0B04
        fingerprint manufacturer: "DAWON_DNS", model: "PM-C140-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS In-Wall Outlet PM-C140-ZB, raw description: 01 0104 0051 01 0A 0000 0002 0003 0004 0006 0019 0702 0B04 0008 0009 0A 0000 0002 0003 0004 0006 0019 0702 0B04 0008 0009

		fingerprint endpointId: "01", profileId: "0104", inClusters: "0000,0003,0006,0004,0005,0008", outClusters: "0019"
        fingerprint endpointId: "02", profileId: "0104", inClusters: "0702,0006"
	}

    tiles(scale: 2)
    {
        multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true)
        {
                tileAttribute("device.switch", key: "PRIMARY_CONTROL")
                {
                        attributeState("on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc")
                        attributeState("off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff")
                }
        }
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2)
        {
                state "default", label:'${currentValue} W'
        }
        valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2)
        {
                state "default", label:'${currentValue} kWh'
        }
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
                state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
                state "default", label:'reset kWh', action:"reset"
        }

        main(["switch"])
        
        details(["switch","power","energy","refresh","reset"])
    }
}

def parse(String description)
{
    log.debug "start: parse() == description is $description"
    
    def event = zigbee.getEvent(description)
    if (event) {
        log.info "start event : $event"
        if (event.name == "power") {
            event.value = event.value/getPowerDiv()
            event.unit = "W"
        } else if (event.name == "energy") {
            event.value = event.value/getEnergyDiv()
            event.unit = "kWh"
        }
        log.info "end event :  == Sending $event"
        sendEvent(event)
    }
    
    List result = []
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (descMap.cluster) {
        log.debug "cluster $descMap.cluster attrId $descMap.attrId encoding $descMap.encoding value $descMap.value result $descMap.result"
        log.debug "descMap enter:"
        // if (descMap?.result != "success") return
        if ((descMap.cluster == "0008")) {
            // Ignore Cluster 0008 for now
        } else if ((descMap.cluster == "0702") && (descMap.attrId == "0000")) {
				log.debug "Found 702/0000 Power Delivered"
                log.debug "$descMap.attrId Power delivered $descMap.value"

                int delivered = Integer.parseInt(descMap.value, 16)
                log.debug sprintf("Power delivered is %d", delivered)
                
                Double current = delivered * getEnergyMult() / getEnergyDiv()
                
                def map = [:]
                map.name = "energy"
                map.value = current
                map.unit = "kWh"
                if (map) {
                    result << createEvent(map)
                    log.info "$descMap.attrId/$descMap.attrId sending $result"
                }
            } else if ((descMap.cluster == "0702") && (descMap.attrId == "0300")) {
				log.debug "Found 702/0300 Unit of Measure"
                log.debug "$descMap.attrId/$descMap.attrId Unit of measure $descMap.value"

            } else if ((descMap.cluster == "0702") && (descMap.attrId == "0301")) {
                log.debug "Found 702/0301 Multiplier"
                log.debug "$descMap.attrId/$descMap.attrId Multiplier $descMap.value"
                int multiplier = Integer.parseInt(descMap.value, 16)
                log.debug sprintf("multiplier is %d", multiplier)

            } else if ((descMap.cluster == "0702") && (descMap.attrId == "0302")) {
				log.debug "Found 702/0302 Divisor"
                log.debug "$descMap.attrId/$descMap.attrId Divisor $descMap.value"
                int divisor = Integer.parseInt(descMap.value, 16)
                log.debug sprintf("divisor is %d", divisor)

            } else if ((descMap.cluster == "0702") && (descMap.attrId == "0400")) {
				log.debug "Found 702/0400 Power"
                log.debug "$descMap.attrId/$descMap.attrId Power $descMap.value"
                int power = Integer.parseInt(descMap.value, 16)
                log.debug sprintf("Current Power Consumption is %d", power)
                
                Double current = power * getPowerMult() / getPowerDiv()
                
                def map = [:]
                map.name = "power"
                map.value = current
                map.unit = "W"
                if (map) {
                    result << createEvent(map)
                    log.info "$descMap.attrId/$descMap.attrId sending $result"
                }
        } else {
            log.error "$descMap.attrId Unhandled read attr - : desc:${description}"
        }
        log.debug "descMap exit:"
    }
    if (description?.startsWith("catchall: 0104 0006 01 01 0140 00") ||
        description?.startsWith("catchall: 0104 0006 02 01 0140 00")) {
        if (description?.endsWith(" 0100") || description?.endsWith(" 1001")) {
            def map = [:]
            map.name = "switch"
            map.value = "on"
            if (map) {
                result << createEvent(map)
                log.debug "Switch On: Sending $result"
            }
		} else if (description?.endsWith(" 0000") || description?.endsWith(" 1000")) {
            def map = [:]
            map.name = "switch"
            map.value = "off"
            if (map) {
                result << createEvent(map)
                log.debug "Switch Off: Sending $result"
            }
        }
    }

    log.debug "end parse() : == returning $result"

    return result
}

def off()
{
    log.debug "start off() :"

    def cmds = []

    cmds += zigbee.off()

    // Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
    cmds += getEnergy()

    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
    cmds += getPower()

    log.debug "end off() : == $cmds"
    return cmds
}

def on()
{
    log.debug "start on() :"

    def cmds = []
    
    cmds += zigbee.on()

    // Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
    cmds += [ "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0000", "delay 2000" ]

    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
    cmds += [ "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0400", "delay 2000" ]

    if (device.getDataValue("model") == "HY0105") {
        cmds += zigbee.command(zigbee.ONOFF_CLUSTER, 0x01, "", [destEndpoint: 0x02])
    }
    log.debug "end on() : == $cmds"
    return cmds
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping()
{
    return refresh()
}

def refresh()
{
    log.debug "refresh"
    zigbee.onOffRefresh() +
    zigbee.electricMeasurementPowerRefresh() +
    zigbee.readAttribute(zigbee.SIMPLE_METERING_CLUSTER, ATTRIBUTE_READING_INFO_SET)
}

def configure()
{
    // this device will send instantaneous demand and current summation delivered every 1 minute
    sendEvent(name: "checkInterval", value: 2 * 60 + 10 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    log.debug "Configuring Reporting"
    return refresh() +
    	   zigbee.onOffConfig() +
           zigbee.configureReporting(zigbee.SIMPLE_METERING_CLUSTER, ATTRIBUTE_READING_INFO_SET, DataType.UINT48, 1, 600, 1) +
           zigbee.electricMeasurementPowerConfig(1, 600, 1) +
           zigbee.simpleMeteringPowerConfig()
}

private int getPowerMult()
{
    1
}

private int getEnergyMult()
{
    1
}

private int getPowerDiv()
{
    100
}

private int getEnergyDiv()
{
    100000
}

private boolean isSengledOutlet()
{
    device.getDataValue("model") == "E1C-NB7"
}

def parseDescriptionAsMap(description)
{
    (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}

private def getEnergy()
{
    // Power delivered, EndPoint 2, Cluster 0702 Attribute 0000
    [ "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0000", "delay 2000" ]
}

private def getPower()
{
    // Instantaneous Demand, EndPoint 2, Cluster 0702 Attribute 0400
    [ "st rattr 0x${device.deviceNetworkId} 2 0x0702 0x0400", "delay 2000" ]
}