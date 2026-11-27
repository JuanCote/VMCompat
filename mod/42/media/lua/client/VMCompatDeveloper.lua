require "ISUI/ISPanel"

local Panel = ISPanel:derive("VMCompatDeveloperPanel")
local panel = nil

local function backendDetail()
    local reader = getFileReader("VMCompatBackendDetails.txt", false)
    if not reader then return nil end
    local schema = reader:readLine(); reader:readLine(); local text = reader:readLine(); reader:close()
    if schema == "1" and text ~= "not requested" then return text end
    return nil
end

local function readMetrics()
    local reader = getFileReader("VMCompatPerformance.txt", false)
    if not reader then return nil end
    local ok, result = pcall(function()
        local lines = {}
        for i = 1, 17 do lines[i] = reader:readLine() end
        if lines[1] ~= "1" then return nil end
        local m = {stamp=tonumber(lines[2]), version=lines[3], total=tonumber(lines[4]),
            count=tonumber(lines[5]), window=tonumber(lines[6]), fps=tonumber(lines[7]),
            mean=tonumber(lines[8]), median=tonumber(lines[9]), p95=tonumber(lines[10]),
            p99=tonumber(lines[11]), peak=tonumber(lines[12]), last=tonumber(lines[13]),
            over50=tonumber(lines[14]), backend=lines[15], age=tonumber(lines[16]), dropped=tonumber(lines[17]) or 0}
        for _, key in ipairs({"stamp","count","window","fps","mean","median","p95","p99","peak","last","over50","age"}) do
            local v = m[key]
            if not v or v ~= v or v < 0 or v == math.huge then return nil end
        end
        return m
    end)
    reader:close()
    if ok then return result end
    return nil
end

function Panel:prerender()
    if not getPlayer() then self:setVisible(false); return end
    local line = math.max(18, getTextManager():getFontHeight(UIFont.Small) + 4)
    self:setHeight(42 + line * 11)
    self:setX(math.max(0, getCore():getScreenWidth() - self.width - 16))
    self:setY(40)
    ISPanel.prerender(self)
    self:drawText("VMCompat developer  |  F8", 12, 10, 0.85, 0.9, 1, 1, UIFont.Small)
    local now = getTimestampMs()
    if now >= self.nextPoll then
        self.nextPoll = now + 1000
        local ok, data = pcall(readMetrics)
        self.metrics = ok and data or nil; self.backendDetail = backendDetail()
    end
    local m = self.metrics
    if m and m.backend and string.find(m.backend, "FAILED Metal opaque", 1, true) then
        self:drawText("METAL FAILED — experiment stopped", 12, 42, 1, 0.2, 0.2, 1, UIFont.Small)
        self:drawText("Restart with opaqueBackend=opengl", 12, 68, 1, 0.7, 0.3, 1, UIFont.Small)
        self:drawText("Error details: Zomboid/vmcompat logs", 12, 94, 1, 0.7, 0.3, 1, UIFont.Small)
        if self.backendDetail then self:drawText(string.sub(self.backendDetail, 1, 70), 12, 120, 1, 0.45, 0.3, 1, UIFont.Small) end
        return
    end
    if not m or now - m.stamp > 3500 or m.stamp - now > 5000 or m.age > 3000 then
        self:drawText("Frame telemetry unavailable / paused", 12, 42, 1, 0.65, 0.3, 1, UIFont.Small)
        return
    end
    if m.backend and string.find(m.backend, "diagnostics off", 1, true) then
        self:drawText("Diagnostics disabled (telemetry=off)", 12, 42, 1, 0.8, 0.3, 1, UIFont.Small)
        return
    end
    if m.count == 0 or m.fps == 0 then
        self:drawText("Waiting for rendered frames...", 12, 42, 1, 0.8, 0.3, 1, UIFont.Small)
        return
    end
    local rows = {
        {"FPS", string.format("%.1f", m.fps)},
        {"Frame average", string.format("%.2f ms", m.mean)},
        {"Median", string.format("%.2f ms", m.median)},
        {"P95 / P99", string.format("%.2f / %.2f ms", m.p95, m.p99)},
        {"Peak in window", string.format("%.2f ms", m.peak)},
        {"Frames > 50 ms", string.format("%d", m.over50)},
        {"Backend", m.backend or "UNKNOWN"},
        {"Window", string.format("%.2f s / %d frames", m.window / 1000, m.count)},
        {"Renderer", m.version or "unknown"},
        {"Telemetry drops", string.format("%d", m.dropped)},
    }
    for i, row in ipairs(rows) do
        local y = 34 + (i - 1) * line
        self:drawText(row[1], 12, y, 0.75, 0.78, 0.82, 1, UIFont.Small)
        self:drawText(row[2], 164, y, 1, 1, 1, 1, UIFont.Small)
    end
    self:drawText(self.backendDetail and string.sub(self.backendDetail,1,70) or "Display frame intervals  |  F8: hide", 12, 34 + 10 * line, 0.55, 0.62, 0.7, 1, UIFont.Small)
end

local function toggle(key)
    if key ~= Keyboard.KEY_F8 or not getPlayer() then return end
    if not panel then
        panel = Panel:new(0, 40, 500, 260)
        panel.nextPoll = 0
        panel.backgroundColor = {r=0.025, g=0.035, b=0.05, a=0.92}
        panel.borderColor = {r=0.3, g=0.4, b=0.55, a=0.9}
        panel:initialise()
        if panel.javaObject then panel.javaObject:setConsumeMouseEvents(false) end
        panel:addToUIManager()
        panel:setVisible(true)
    else
        panel:setVisible(not panel:getIsVisible())
    end
    if panel:getIsVisible() then panel.nextPoll = 0; panel:bringToTop() end
end

local nextFaultPoll = 0
local faultShown = false
Events.OnTick.Add(function()
    if faultShown or not getPlayer() or getTimestampMs() < nextFaultPoll then return end
    nextFaultPoll = getTimestampMs() + 250
    local m = readMetrics()
    if m and m.backend and string.find(m.backend, "FAILED Metal opaque", 1, true) then
        faultShown = true
        if not panel then toggle(Keyboard.KEY_F8) end
        panel.metrics = m; panel:setVisible(true); panel:bringToTop()
        local controls = UIManager.getSpeedControls()
        if controls then controls:SetCurrentGameSpeed(0) end
    end
end)
Events.OnKeyPressed.Add(toggle)
print("[VMCompat] Developer panel: F8 (Fn+F8 on Mac). Rolling frame intervals; telemetry refreshes once a second.")
