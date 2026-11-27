"""Isolated package checks. Does not start PZ, alter the installed mod, or bypass approvals.

Preseeded-registry negative probes verify fail-closed behavior only. They are explicitly
NOT evidence of a successful first-install signing/approval user flow.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import zipfile

from build_workshop import BUILD, GAME, HERE, JDK, OLDPOINT, OUT, PACKAGE, ROOT, VP, sha

def main():
    # The production agent conflict check inspects the agent's full path. Keep
    # the test agent outside the checkout, whose directory may be "VMCompat".
    with tempfile.TemporaryDirectory(prefix="zb-fixture-") as directory:
        run_checks(Path(directory))

def run_checks(sandbox):
    if not (GAME/'ZombieBuddy.jar').is_file():
        raise SystemExit('Install ZombieBuddy or set VMCOMPAT_GAME_JAVA_DIR to its game Java directory.')
    tests=OUT/'tests';tests.mkdir(parents=True,exist_ok=True)
    public=PACKAGE/'Contents/mods/VMCompat/42/media/java/client/VMCompat.jar'
    if not public.is_file():
        raise SystemExit('Build VMCompat first with python3 tools/build_workshop.py.')
    fixture=BUILD/'tests';fixture.mkdir(exist_ok=True)
    cp=':'.join(map(str,[public,GAME/'projectzomboid.jar',VP]))
    oldpoint = OLDPOINT
    # Existing checks are compiled against the public artifact, never an installed VMCompat JAR.
    subprocess.run([str(JDK/'javac'),'--release','25','-cp',cp,'-d',str(fixture),*[str(p) for p in (HERE/'tests').rglob('*.java')],*[str(p) for p in (ROOT/'src/test/java').rglob('*.java')]],check=True)
    for name in ['projectzomboid.jar','ZombieBuddy.jar']:
        shutil.copy2(GAME/name,sandbox/name)
    assert not (sandbox/'VMCompat.jar').exists() and not (sandbox/'VMPerf.jar').exists()
    java = os.environ.get('VMCOMPAT_TEST_JAVA') or GAME.parent/'PlugIns/jre-aarch64/Contents/Home/bin/java'
    results=[]
    def run(label,main,args=(),props=None,home=None,public_cp=True,buddy=False,fixture_mode=False,vp_cp=True,extra_cp=()):
        folder=tests/label;folder.mkdir(parents=True,exist_ok=True);home=home or folder/'home';home.mkdir(parents=True,exist_ok=True)
        values={'user.home':str(home),'vmcompat.config':str(home/'Zomboid/vmcompat.properties'),'vmcompat.root':str(folder/'runtime'),'vmcompat.luaRoot':str(home/'Zomboid/Lua'),'vmcompat.resultDir':str(folder),'vmcompat.testMode':'true',**(props or {})}
        classpath=[fixture,sandbox/'projectzomboid.jar',sandbox/'ZombieBuddy.jar']
        if vp_cp:classpath.append(VP)
        classpath.extend(extra_cp)
        if public_cp:classpath.insert(0,public)
        command=[str(java),'-XstartOnFirstThread','--enable-native-access=ALL-UNNAMED','--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED','-Djava.awt.headless=true','-Djava.library.path='+str(GAME),*['-D'+k+'='+str(v) for k,v in values.items()]]
        if buddy:command+=['-javaagent:'+str(sandbox/'ZombieBuddy.jar')]
        command+=['-cp',':'.join(map(str,classpath)),main,*map(str,args)]
        started=time.monotonic();p=subprocess.run(command,cwd=GAME,capture_output=True,text=True,timeout=120)
        log=p.stdout+p.stderr;(folder/'process.log').write_text(log)
        passed=p.returncode==0 and '[ZB] ERROR' not in log
        row={'name':label,'passed':passed,'exit_code':p.returncode,'seconds':round(time.monotonic()-started,3),'public_jar_sha256':sha(public),'only_agent':'ZombieBuddy' if buddy else None,'fixture_only':fixture_mode}
        results.append(row);print(label,passed,flush=True)
        if not passed:print(log[-2400:],flush=True)
        return passed
    run('defaults','vmcompat.WorkshopChecks',['defaults'])
    run('unit','vmcompat.WorkshopChecks',['unit',tests/'selection.txt'])
    run('sampler-helper-overloads','vmcompat.SamplerFunctionsTests')
    h=tests/'override-home';(h/'Zomboid').mkdir(parents=True,exist_ok=True);(h/'Zomboid/vmcompat.properties').write_text('pickEvery=3\ntelemetry=full\n')
    run('overrides','vmcompat.WorkshopChecks',['overrides'],{'vmcompat.pickEvery':'1','vmcompat.telemetry':'off'},home=h)
    h=tests/'diag-home';(h/'Zomboid/Lua').mkdir(parents=True,exist_ok=True);(h/'Zomboid/Lua/VMCompatDiagnostics.properties').write_text('telemetry=full\nframeBreakdown=true\npickingDetailed=true\n')
    run('diag','vmcompat.WorkshopChecks',['diag'],home=h)
    run('lua','vmcompat.WorkshopChecks',['lua',*sorted((PACKAGE/'Contents/mods/VMCompat/42/media/lua').rglob('*.lua'))])
    run('hud','vmcompat.WorkshopChecks',['hud'])
    run('hang-progress','vmcompat.HangTraceTests')
    run('hang-progress-off','vmcompat.HangTraceTests',props={'vmcompat.telemetry':'off'})
    run('retention','vmcompat.WorkshopChecks',['retention'])
    run('adapter-linkage','vmcompat.AdapterLinkTests',[sandbox/'projectzomboid.jar',VP])
    run('native-output','vmcompat.NativeTraceTests',props={'vmcompat.nativeCrashTrace':'true'})
    run('components/native-sampler-contract','workshoptests.ComponentRunner',['vmcompat.NativeSamplerContractTests',VP],buddy=True,fixture_mode=True)
    run('components/recursive-transforms','workshoptests.ComponentRunner',['vmcompat.TransformCoverageTests'],buddy=True,fixture_mode=True)
    for label,selection,registry,exists,expected in [
        ('no-preload-approval','',False,True,False),
        ('disabled-stale-preload','Viewpoint,ZombieBuddy',True,True,True),
        ('missing-dependency','VMCompat,ZombieBuddy',True,True,True),
        ('unsupported-game','VMCompat,ZombieBuddy,Viewpoint',True,True,True),
        ('removed-jar','',True,False,False),
    ]:
        home=tests/label/'home';mods=home/'Zomboid/mods';mods.mkdir(parents=True,exist_ok=True)
        (mods/'default.txt').write_text('VERSION = 1,\nmods\n{\n'+''.join(' mod = '+m+',\n' for m in selection.split(',') if m)+'}\nmaps {}\n')
        copy=home/'Workshop/content/108600/private-preview/mods/VMCompat';(copy/'42/media/java/client').mkdir(parents=True,exist_ok=True);(copy/'common').mkdir(exist_ok=True)
        jar=copy/'42/media/java/client/VMCompat.jar';info=copy/'common/mod.info';shutil.copy2(PACKAGE/'Contents/mods/VMCompat/common/mod.info',info)
        if exists:shutil.copy2(public,jar)
        if registry:
            config=home/'.zombie_buddy';config.mkdir(exist_ok=True)
            # Test fixture setup only, models a previously approved preload record; never installed on the user's profile.
            (config/'config.json').write_text(json.dumps({'trusted_authors':[],'preload_mods':{'VMCompat':{'infPath':str(info),'jarPath':str(jar)}},'auto_fix_mod_order':True,'fix_approval_dialog_cursor':True}))
        state='DISABLED' if label=='disabled-stale-preload' else 'INCOMPATIBLE'
        run(label,'workshoptests.LaunchProbe',[str(expected).lower(),state],home=home,public_cp=False,buddy=True,vp_cp=False)
    # Component coverage, bypassing the failed release bootstrap only within this test harness.
    # Updated local dependency libraries are recorded; these are not qualification of PZ 42.22.
    for selected in ['false','true']:
        run('components/partial-packing-'+selected,'workshoptests.ComponentRunner',['vmcompat.PartialPackingTests'],{'vmcompat.telemetry':'full','vmcompat.packingArrayOnly':selected},buddy=True,fixture_mode=True)
    for fragment in ['model_gbuffer.frag','model_shadow.frag','model_outline.frag','model_glass.frag']:
        run('components/model-draw-'+fragment,'workshoptests.ComponentRunner',['vmcompat.ModelDrawTests'],{'vmcompat.modelFragment':fragment},buddy=True,fixture_mode=True)
    for fragment in ['gbuffer_shell.frag','far_shell.frag']:
        run('components/far-draw-'+fragment,'workshoptests.ComponentRunner',['vmcompat.FarDrawTests'],{'vmcompat.farFragment':fragment},buddy=True,fixture_mode=True)
    run('components/packing-contexts','workshoptests.ComponentRunner',['vmcompat.PackingContextTests'],buddy=True,fixture_mode=True)
    run('components/packing-formats','workshoptests.ComponentRunner',['vmcompat.PackingFormatsTests'],buddy=True,fixture_mode=True)
    run('components/packing-eviction','workshoptests.ComponentRunner',['vmcompat.PackingEvictionTests'],buddy=True,fixture_mode=True)
    run('components/packing-residency','workshoptests.ComponentRunner',['vmcompat.PackingResidencyTests'],buddy=True,fixture_mode=True)
    for breakdown in ['false','true']:
        run('components/packing-phases-'+breakdown,'workshoptests.ComponentRunner',['vmcompat.PackingResidencyTests'],{'vmcompat.telemetry':'full','vmcompat.packingBreakdown':breakdown,'vmcompat.measurePackingPhases':'true','vmcompat.packingTableCache':'false'},buddy=True,fixture_mode=True)
    run('components/packing-tables-warm','workshoptests.ComponentRunner',['vmcompat.PackingResidencyTests'],{'vmcompat.telemetry':'full','vmcompat.packingBreakdown':'true','vmcompat.measurePackingPhases':'true','vmcompat.expectPackingReuse':'true'},buddy=True,fixture_mode=True)
    for cache in ['false','true']:
        run('components/packing-tables-queue-'+cache,'workshoptests.ComponentRunner',['vmcompat.PackingTableTests'],{'vmcompat.telemetry':'full','vmcompat.packingTableCache':cache,'vmcompat.packingChurn':'true'},buddy=True,fixture_mode=True)
    for tracking in ['false','true']:
        run('components/packing-sampling-'+tracking,'workshoptests.ComponentRunner',['vmcompat.PackingSamplingTests'],{'vmcompat.packingParameterTracking':tracking},buddy=True,fixture_mode=True)
    for native in ['false','true']:
        run('components/native-words-'+native,'workshoptests.ComponentRunner',['vmcompat.WordStorageTests'],{'vmcompat.nativeBatchSamplers':native},buddy=True,fixture_mode=True)
    run('components/lighting-preset-switch','workshoptests.ComponentRunner',['vmcompat.LightingPresetTests'],buddy=True,fixture_mode=True)
    run('components/wall-and-mask','workshoptests.ComponentRunner',['vmcompat.WallAndMaskTests'],buddy=True,fixture_mode=True)
    run('components/compressed-copy','workshoptests.ComponentRunner',['vmcompat.CompressedCopyTests'],buddy=True,fixture_mode=True)
    run('components/texture-feedback','workshoptests.ComponentRunner',['vmcompat.FeedbackTests'],buddy=True,fixture_mode=True)
    run('components/read-only-storage','workshoptests.ComponentRunner',['vmcompat.StorageBuffersTests'],buddy=True,fixture_mode=True)
    run('components/cpu-pose-compatibility','workshoptests.ComponentRunner',['vmcompat.PoseCompatibilityTests'],buddy=True,fixture_mode=True)
    if oldpoint and oldpoint.is_file():
        run('components/oldpoint-cutaway','workshoptests.ComponentRunner',['vmcompat.OldPointCompatibilityTests'],
            buddy=True,fixture_mode=True,extra_cp=[oldpoint])
    for cls in ['TextureCommandTests','ResourceMutationTests','BatchStateTests','CharacterTests','ImGuiTests','MenuBufferTests','OpaqueTests','ArrayClearTests','ViewpointImGuiTests']:
        extra=['unused',tests/'components'/cls/'images'] if cls=='MenuBufferTests' else []
        run('components/'+cls,'workshoptests.ComponentRunner',[cls,*extra],{'vmcompat.telemetry':'full','vmcompat.pickingPath':'original','vmcompat.pickEvery':'1'},buddy=True,fixture_mode=True)
    run('components/approval-inherited-state','workshoptests.ComponentRunner',['ApprovalImGuiTests','--full-dialog','--poison-state'],buddy=True,fixture_mode=True)
    run('components/approval-mipmap-sampler','workshoptests.ComponentRunner',['ApprovalImGuiTests','--full-dialog','--mipmap-sampler','--pending-row'],buddy=True,fixture_mode=True)
    run('components/startup-ui-capture','workshoptests.ComponentRunner',['ApprovalImGuiTests','--full-dialog','--pending-row','--capture'],{'vmcompat.startupUiCapture':'true'},buddy=True,fixture_mode=True)
    run('components/compat-imgui-switch','workshoptests.ComponentRunner',['ImGuiTests'],{'vmcompat.nativeImGui':'false'},buddy=True,fixture_mode=True)
    run('components/approval-instanced-host-vao','workshoptests.ComponentRunner',['ApprovalImGuiTests','--full-dialog','--pending-row','--instanced-vao'],buddy=True,fixture_mode=True)
    run('components/approval-instanced-compat-path','workshoptests.ComponentRunner',['ApprovalImGuiTests','--full-dialog','--pending-row','--instanced-vao'],{'vmcompat.nativeImGui':'false'},buddy=True,fixture_mode=True)
    for path in ['original','depthStaging']:
        run('components/pick3-'+path,'workshoptests.ComponentRunner',['OpaqueTests','--picking-rate'],{'vmcompat.telemetry':'full','vmcompat.pickingPath':path,'vmcompat.pickEvery':'3'},buddy=True,fixture_mode=True)
    for mode in ['full','hud','off']:
        run('components/pick017-'+mode,'workshoptests.ComponentRunner',['OpaqueTests','--picking-rate'],{'vmcompat.telemetry':mode,'vmcompat.pickingPath':'depthStaging','vmcompat.pickEvery':'3','vmcompat.pickingDetailed':'true','vmcompat.busyProbe':'true','vmcompat.noAskPrefix':'4','vmcompat.resizeProbe':'true'},buddy=True,fixture_mode=True)
    run('components/GpuTests','workshoptests.ComponentRunner',['GpuTests',GAME],{'vmcompat.telemetry':'full'},buddy=True,fixture_mode=True)
    run('components/startup-shader-catalog','workshoptests.ComponentRunner',['vmcompat.StartupShadersTests',VP],buddy=True,fixture_mode=True)
    run('components/final-composition','workshoptests.ComponentRunner',['vmcompat.CompositionInspect'],{'vmcompat.telemetry':'full','vmcompat.frameBreakdown':'true','vmcompat.fixtureViewpointJar':str(VP),'vmcompat.finalDump':str(tests/'components/final-composition/bytecode')},buddy=True,fixture_mode=True)
    with zipfile.ZipFile(public) as z:
        names=z.namelist();manifest=z.read('META-INF/MANIFEST.MF').decode()
        assert 'Premain-Class:' not in manifest and 'ZB-Preload: true' in manifest
        assert not any(n.endswith(('.dylib','.so','.dll','.metal')) or '/opaque/' in n or '/tests/' in n for n in names)
        assert all(n.startswith(('vmcompat/','META-INF/')) for n in names)
    modinfo=(PACKAGE/'Contents/mods/VMCompat/common/mod.info').read_text()
    assert not any(line.startswith(('versionMin=','versionMax=')) for line in modinfo.splitlines())
    report={'passed':all(r['passed'] for r in results),'jar_sha256':sha(public),'results':results,'game_launched':False,'installed':False,'published':False,'standard_approval_flow':'NOT_TESTED: preload registry seeded only in isolated probes; standard UI approval remains manual','current_experimental_pair':'PZ 42.22.2 / Viewpoint 0.1.7 / ZombieBuddy 2.3.4','fixture_game_sha256':sha(sandbox/'projectzomboid.jar'),'fixture_viewpoint_sha256':sha(VP),'component_qualification_limit':'Component tests are not acceptance of the subscription flow or proof of full game FPS.'}
    (OUT/'checks.json').write_text(json.dumps(report,indent=2)+'\n')
    assert report['passed'],'Inspect individual failures; preview remains blocked'
if __name__=='__main__':main()
