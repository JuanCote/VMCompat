require = function() end
UIFont = { Small = 1 }
TEST_DIALOGS, TEST_LOGS = {}, {}
print = function(text) TEST_LOGS[#TEST_LOGS+1] = text end
getCore = function() return {
    getScreenWidth = function() return TEST_WIDTH end,
    getScreenHeight = function() return TEST_HEIGHT end,
} end
getTextManager = function() return {
    getFontHeight = function() return 14 * TEST_SCALE end,
    MeasureStringX = function(self, font, text)
        local _,continuations = text:gsub("[\128-\191]", "")
        return (#text-continuations) * 7 * TEST_SCALE
    end,
} end
getText = function(key)
    local t = TEST_LANGUAGE == "RU" and UI_RU or UI_EN
    return t and t[key] or key
end
function string:split(separator)
    local out = {}
    for line in (self .. "\n"):gmatch("(.-)\n") do out[#out+1] = line end
    return out
end
ISPanelJoypad = {}
function ISPanelJoypad:derive(name)
    local class = {}; class.__index=class
    setmetatable(class, {__index=self}); return class
end
function ISPanelJoypad:new(x,y,w,h)
    local o={x=x,y=y,width=w,height=h};self.__index=self
    setmetatable(o,self);return o
end
function ISPanelJoypad:getWidth() return self.width end
function ISPanelJoypad:getHeight() return self.height end
function ISPanelJoypad:setX(x) self.x=x end
function ISPanelJoypad:setY(y) self.y=y end
function ISPanelJoypad:getMouseX() return 100000 end
function ISPanelJoypad:getMouseY() return 100000 end
function ISPanelJoypad:addToUIManager() TEST_DIALOGS[#TEST_DIALOGS+1]=self end
function ISPanelJoypad:addChild() end
ISPanel = {initialise=function() end}
ISButton = {new=function() return {
    initialise=function() end, instantiate=function() end, enableAcceptColor=function() end,
} end}
TEST_CALLBACKS={}
Events={
    OnMainMenuEnter={Add=function(fn) TEST_CALLBACKS.menu=fn end},
    OnKeyPressed={Add=function(fn) TEST_CALLBACKS.key=fn end},
}
Keyboard={KEY_F9=9}
WorkshopBootstrap={active=function() return TEST_STATE=="ACTIVE" end,state=function() return TEST_STATE end}
TEST_DIAGNOSTICS="telemetry=hud"
getFileReader=function(name)
    local lines = name=="VMCompatStartup.txt" and {"1",TEST_STATE,"LONG_TECHNICAL_REASON_DO_NOT_DISPLAY"} or {TEST_DIAGNOSTICS:match("([^\n]+)")}
    local index=0
    return {readLine=function() index=index+1;return lines[index] end,close=function() end}
end
getFileWriter=function(name)
    assert(name=="VMCompatDiagnostics.properties")
    return {write=function(self,text) TEST_DIAGNOSTICS=text end,close=function() end}
end
