-- Status only: Lua never activates compatibility or changes Java code approval.
require "ISUI/ISModalDialog"
local shown = false

local function label(key, fallback)
    local text = getText(key)
    return text and text ~= key and text or fallback
end

local function centeredDialog(text)
    local screenW, screenH = getCore():getScreenWidth(), getCore():getScreenHeight()
    local limit = math.max(120, screenW - 64)
    local lines, line = {}, ""
    for word in text:gmatch("%S+") do
        local nextLine = line == "" and word or line .. " " .. word
        if line ~= "" and getTextManager():MeasureStringX(UIFont.Small, nextLine) > limit then
            lines[#lines + 1] = line
            line = word
        else
            line = nextLine
        end
    end
    if line ~= "" then lines[#lines + 1] = line end
    -- PZ's constructor expands the box to fit its text. Center its final size,
    -- after construction/initialisation; x/y=0 would instead follow the mouse.
    local box = ISModalDialog:new(1, 1, math.min(420, screenW - 32), 120,
        table.concat(lines, "\n"), false, nil, nil)
    box:initialise()
    box:setX(math.max(0, math.floor((screenW - box:getWidth()) / 2)))
    box:setY(math.max(0, math.floor((screenH - box:getHeight()) / 2)))
    box:addToUIManager()
end

local function startupStatus()
    if shown then return end
    shown = true
    if WorkshopBootstrap and WorkshopBootstrap.active() and WorkshopBootstrap.auditLoading() then return end
    local state = WorkshopBootstrap and WorkshopBootstrap.state() or "INACTIVE"
    local reader = getFileReader("VMCompatStartup.txt", false)
    if reader then
        reader:readLine(); local fileState=reader:readLine(); local reason=reader:readLine();reader:close()
        if not WorkshopBootstrap and fileState and fileState ~= "ACTIVE" then state=fileState end
        if WorkshopBootstrap and reason then print("[VMCompat startup] " .. tostring(state) .. ": " .. reason)
        else print("[VMCompat startup] Java entrypoint unavailable in this process; prior startup status is not proof of active loading.") end
    end
    if state == "INCOMPATIBLE" or state == "FAILED" then
        centeredDialog(label("UI_VMCompat_StartupError", "VMCompat could not start. See the log."))
    else
        centeredDialog(label("UI_VMCompat_Restart", "Restart the game to enable VMCompat."))
    end
end
if Events.OnMainMenuEnter then Events.OnMainMenuEnter.Add(startupStatus) end

-- Explicit diagnostic opt-in for the next process; only our own Lua settings file is written.
Events.OnKeyPressed.Add(function(key)
    if key ~= Keyboard.KEY_F9 then return end
    local enabled=false
    local reader=getFileReader("VMCompatDiagnostics.properties",false)
    if reader then local line=reader:readLine();enabled=line=="telemetry=full";reader:close() end
    local writer=getFileWriter("VMCompatDiagnostics.properties",true,false)
    if not writer then return end
    if enabled then writer:write("telemetry=hud\nframeBreakdown=false\npickingDetailed=false\n")
    else writer:write("telemetry=full\nframeBreakdown=true\npickingDetailed=true\n") end
    writer:close()
    local text=enabled and "VMCompat detailed diagnostics OFF after restart." or "VMCompat detailed diagnostics ON after restart (120 seconds / approximately 32 MiB per recording). Logs: Zomboid/vmcompat-workshop. Existing vmcompat.properties or JVM options take precedence."
    print("[VMCompat] "..text)
    centeredDialog(enabled
        and label("UI_VMCompat_DiagnosticsOff", "Diagnostics will be disabled after restart.")
        or label("UI_VMCompat_DiagnosticsOn", "Diagnostics will be enabled after restart."))
end)
