/**
 *  Someone's Home
 *
 *  Version 1.0 - Justin Miller
 *		- Started with work done by Tim Slagle (https://github.com/tslagle13/SmartThings/tree/master/Director-Series-Apps/Vacation-Lighting-Director)
 *		- adds more inputs to better control timing between light on/off
 *		- more randomization
 *		- refactoring scheduling code so as to make better use of system resources
 *
 *  Copyright 2015 Justin Miller
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

// Automatically generated. Make future change here.
definition(
		name: "Someone's Home!",
		namespace: "justinmiller61",
		author: "Justin Miller",
		description: "Randomly turn on/off lights to simulate the appearance of a occupied home while you are away.",
		iconUrl: "http://icons.iconarchive.com/icons/custom-icon-design/mono-general-2/512/settings-icon.png",
		iconX2Url: "http://icons.iconarchive.com/icons/custom-icon-design/mono-general-2/512/settings-icon.png"
		)

preferences {
	page(name: "mainPage")
    page(name: "timeIntervalInputStart")
    page(name: "timeIntervalInputEnd")
    page(name: "moreOptions")
}

def mainPage() {

	dynamicPage(name: "mainPage", title: "Status", install: true, uninstall: true) {
        section("Which mode change triggers the simulator? (This app will only run in selected mode(s))") { 
            input name: "newMode", type: "mode", title: "Which?", multiple: true, required: true 
        }

        section("Light switches to turn on/off") { 
            input name: "switches", type: "capability.switch", title: "Switches?", multiple: true, required: true 
        }

        section("Number of active lights at any given time") { 
            input name: "number_of_active_lights", type: "number", title: "Active?" 
        }

		section("How many seconds to wait after turning all lights off, before starting the next cycle.") {
        	input name: "cycle_delay", type: "number", title: "Seconds?"
        }

        section("Minimum number of minutes for each cycle") { 
            input name: "frequency_minutes", type: "number", title: "Minutes?" 
        }
        
        section {
        	href name: "moreOptions", title: "More Options", page: "moreOptions", state: hasMoreOptions()
        }

	}
}

def moreOptions() {

    def daysMap = [
        "Monday", 
        "Tuesday", 
        "Wednesday", 
        "Thursday", 
        "Friday", 
        "Saturday", 
        "Sunday"]

	dynamicPage(name: "moreOptions", title: "More", nextPage: "mainPage") {    
        section("Maximum number of minutes for each cycle (if not specified, will use minimum)") { 
	    	paragraph "If you specify a both a minimum and a maximum number of minutes for a cycle, then a random number of minutes between the two will be chosen."
			input name: "frequency_minutes_end", type: "number", title: "Minutes?", required: false 
        }

        section("How many seconds to pause between turning off each light") { 
            input name: "light_off_delay", type: "number", title: "Seconds?", required: false 
        }

        section("How many seconds to pause between turning on each light") { 
            input name: "light_on_delay", type: "number", title: "Seconds?", required: false 
        }

        section("People") { 
            input name: "people", type: "capability.presenceSensor", title: "If these people are home do not change light status", required: false, multiple: true
        }

		section("Day and/or Time") {
         	href "timeIntervalInputStart", title: "Only during a certain time", description: getTimeLabel(starting, ending), state: getInputState(starting || ending)
            input name: "days", type: "enum", title: "Only on certain days of the week", description: "Days?", multiple: true, required: false, options: daysMap
        }

		section() {
            label title:"Assign a name", required:false
            input name: "falseAlarmThreshold", type: "decimal", title: "Delay to start simulator... (defaults to 2 min)", description: "Minutes?", required: false       
    	}
    }
}

def timeIntervalInputStart() {
	dynamicPage(name: "timeIntervalInputStart", title: "Only during a certain time", nextPage: "timeIntervalInputEnd", install: false) {
	    section {
            input "starting", "time", title: "Starting", required: false
        }
    }
}

def timeIntervalInputEnd() {
	dynamicPage(name: "timeIntervalInputEnd", title: "Only during a certain time", nextPage: "moreOptions") {
	    section {
            input "ending", "time", title: "Ending (required if starting time is specified)", required: starting
        }
    }
}

def hasMoreOptions() {
	starting || ending || days || delay || name || falseAlarmThreshold || frequency_minutes_end || people || light_off_delay || light_on_delay ? "complete" : null
}

def shouldHide() {
	return !(days || starting || ending || delay || name)
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe();
	unschedule();
	initialize()
}

def initialize(){
	if (newMode != null) {
		subscribe(location, modeChangeHandler)
        runApp(location.mode)
	}
}

def modeChangeHandler(evt) {
	runApp(evt.value)
}

def runApp(theMode) {
	log.debug "Mode change to: ${theMode}"
    
	// Have to handle when they select one mode or multiple
	if (newMode.any{ it == theMode } || newMode == theMode) {
		def delay = (falseAlarmThreshold ?: 2) * 60
        def startTime = calculateStartTimeFromInput()
        
        //calculateStartTimeFromInput COULD return a value that is smaller than now(), however
        //if it did, the resulting subtraction would be negative but so close to 
        //zero that it wouldn't really matter for this comparison.
        if((startTime.time - now()) <= (delay * 1000)) {
        	log.debug("Adding $delay second delay")
			delay += startTime[Calendar.SECOND]
            startTime[Calendar.SECOND] = delay
        }
        
        log.debug("Will run at ${startTime}")
        runOnce(startTime, scheduleCheck)
	}
	else {
		//don't turn off lights if anyone is home
		unschedule()

		if(people && anyoneIsHome()){
			log.debug("Stopping Check for Light")
		}
		else{
			log.debug("Stopping Check for Light and turning off all lights")
			switches.off()
		}
	}
}

def calculateStartTimeFromInput() {
	def now = new Date()
	def nextFire = new Date(now.time)
    
	if(starting) {
    	def startingToday = timeToday(starting, location.timeZone)
        def endingToday = timeTodayAfter(startingToday, ending, location.timeZone)
    	if(!timeOfDayIsBetween(startingToday, endingToday, now, location.timeZone)) {
            nextFire = timeTodayAfter(now, starting, location.timeZone)
        }
    }
        
	if(days) {
    	def oldNextFire = nextFire
    	nextFire = nextOccurrence(nextFire, days)
        
        if((oldNextFire - nextFire) && !starting) {
        	//if there was no starting time, then clearTime, which sets to midnight
            nextFire = nextFire.clearTime()
        }
    }
    
    log.debug("Next fire date is: ${nextFire}")
    return nextFire;
}

def nextOccurrence(time, daysOfWeek) {
	def daysMap = [
    	"Monday": 2,
        "Tuesday": 3,
        "Wednesday": 4,
        "Thursday": 5,
        "Friday": 6,
        "Saturday": 7,
        "Sunday": 1 ]
        
	def daysInt = daysOfWeek instanceof String ? [ daysMap[daysOfWeek] ] : daysOfWeek.collect { daysMap[it] }.sort()
    
    def dayOfWeek = time[Calendar.DAY_OF_WEEK]
    def nextDay = daysInt.find { day -> day >= dayOfWeek } ?: daysInt.first()

	//calculate the number of days between the two days of the week
    def daysBetween = (nextDay < dayOfWeek ? 7 : 0) + nextDay - dayOfWeek
    
    //add to DAY_OF_MONTH. Will cause 'time' to roll if necessary
	time[Calendar.DAY_OF_MONTH] = time[Calendar.DAY_OF_MONTH] + daysBetween        
    time
}

// We want to turn off all the lights
// Then we want to take a random set of lights and turn those on
// Then run it again when the frequency demands it
def scheduleCheck(evt) {
	log.debug("Running scheduleCheck")
    
	if(allOk){
		log.debug("Running")
		turnOn(evt)
	}
	//Check to see if mode is ok but not time/day.  If mode is still ok, check again after frequency period.
	else if (modeOk) {
		log.debug("mode OK.  Running again")
		switches.off()
        runOnce(calculateStartTimeFromInput(), scheduleCheck)
	}
	//if none is ok turn off frequency check and turn off lights.
	else if(people){
    	//necessary?
		//don't turn off lights if anyone is home
		if(anyoneIsHome()){
			log.debug("Stopping Check for Light")
		}
		else{
			log.debug("Stopping Check for Light and turning off all lights")
			switches.off()
		}
	}
}

def turnOn(evt, theSwitches = null, numOn = null) {
	theSwitches = theSwitches ?: allOff.clone()
	numOn = (numOn ?: allOn.size()) 

	log.debug("$numOn lights are on. I have ${theSwitches.size()} available switches")
	log.debug("Turning on lights ${light_on_delay ? 'slowly' : 'quicky'}")

    if (numOn < number_of_active_lights) {
        def theSwitch = getRandomN(1, theSwitches, !light_on_delay)
        log.debug("Turning on ${theSwitch.label}")
        theSwitch.on()
        
    	light_on_delay ? runIn(light_on_delay, turnOnHandler) : turnOn(evt, theSwitches, numOn + 1)
    } else {
        runIn(getNextRunTime(), turnOff)
    }
}

def turnOff(evt) {
	if(!light_off_delay) {
    	log.debug("Turning off all lights quickly")
    	switches.off()
        runIn(cycle_delay, scheduleCheck)
    } else {
    	log.debug("Turning off lights slowly")
        
        if (allOn.size() > 0) {
        	log.debug("Turning off ${allOn.first().label}")
	        allOn.first().off()
            runIn(light_off_delay, turnOff)
        } else {
            runIn(cycle_delay, scheduleCheck)
        }
    }
}

def turnOnHandler(evt) {
	turnOn(evt)
}

def getNextRunTime() {
    def freq = getRandomBetween(frequency_minutes, frequency_minutes_end) * 60
    log.debug("Next cycle time: ${freq}")
    return freq
}

def switchesWithState(theState) {
	switches.findAll { thisSwitch -> (thisSwitch.currentSwitch ?: "off") == theState }
}

def getAllOn() {
	switchesWithState("on")
}

def getAllOff() {
	switchesWithState("off")
}

def getRandomN(num, theList, remove = false) {
	def r = new Random()
	def listCopy = remove ? theList : theList.clone()
    def subset = []
    
    num = Math.min(listCopy.size(), num)    
	num.times { subset << listCopy.remove(r.nextInt(listCopy.size())) }
    
    subset.size() == 1 ? subset.first() : subset
}

def getRandomBetween(start, end) {
	start && end ? (new Random().nextInt(end - start + 1) + start) : start
}

//below is used to check restrictions
private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !newMode || newMode.contains(location.mode)
	log.trace "modeOk = $result"
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
	log.trace "daysOk = $result"
	result
}

private getTimeOk() {
    def result = true
    if (starting && ending) {
        def todayStart = timeToday(starting, location.timeZone)
        def todayEnd = timeTodayAfter(todayStart, ending, location.timeZone)
        def now = new Date()
        result = timeOfDayIsBetween(todayStart, todayEnd, now, location.timeZone)
    }
    return result
}

def getTimeLabel(starting, ending){

	def timeLabel = "Tap to set"

	if(starting && ending){
		timeLabel = "Between" + " " + hhmm(starting) + " "  + "and" + " " +  hhmm(ending)
	}
	else if (starting) {
		timeLabel = "Start at" + " " + hhmm(starting)
	}
	else if(ending){
		timeLabel = "End at" + hhmm(ending)
	}
	timeLabel
}

private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

def getInputState(hasInput){
	return hasInput ? "complete" : ""
}

private anyoneIsHome() {
	def result = false

	if(people.findAll { it?.currentPresence == "present" }) {
		result = true
	}

	log.debug("anyoneIsHome: ${result}")

	return result
}

