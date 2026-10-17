#!/usr/bin/env python3
"""Linux host ASAN/LSAN contrast of actual bundled OpenCC and Marisa resources.

Usage: python3 script/quality/run_opencc_resource_controls.py build/opencc-resources

Builds two isolated copies with one compiler worker by default (at most two). The original
must expose the known direct-file header allocation and filename/writer handle
failures; the patched copy must reject the same malformed inputs without
retaining resources and complete every valid retry. Both config-provider paths
must remain clean: pinned OpenCC loads their dictionaries from owned buffers.
Original failing logs are preserved. This does
not build an APK or establish Android device acceptance. Requires clang++,
CMake, Ninja, Python 3, /proc/self/fd and /dev/full on Linux.
"""
import argparse,hashlib,json,os,re,shutil,subprocess,sys,time
from pathlib import Path
if sys.flags.optimize:
    raise SystemExit(
        "OpenCC resource gate refuses optimized Python (-O/-OO); "
        "its control assertions must remain enabled"
    )
ROOT=Path(__file__).resolve().parents[2]
DRAFT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("output",type=Path,help="New directory for both native builds, original failures and patched controls")
parser.add_argument("--workers",type=int,choices=(1,2),default=1,
                    help="Compiler workers for each serial native control build (default: 1)")
parser.add_argument("--cmake",default="cmake",help="Host CMake executable (for example SDK CMake3.31.6)")
parser.add_argument("--compiler",default="clang++",help="ASAN/LSAN host C++ compiler")
args=parser.parse_args()
OUT=args.output.resolve();OUT.mkdir(parents=True,exist_ok=False)
SOURCE=ROOT/'app/src/main/jni/OpenCC'
THIRD=ROOT/'app/src/main/jni/cmake/ThirdPartyCmake.cmake'
SAFETY=ROOT/'app/src/main/jni/cmake/OpenccResourceSafety.cmake'
LOOKUP=ROOT/'app/src/main/jni/cmake/OpenccLookupCompat.cmake'
PLUGIN=ROOT/'app/src/main/jni/cmake/OpenccPluginCompat.cmake'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):p.write_text(json.dumps(value,indent=2)+'\n')
report={'status':'running','control_passed':False,'purpose':'Native host diagnostic controls of actual bundled OpenCC/Marisa; not Android runtime or final release acceptance','compiler_workers':args.workers,'inputs':{str(p):sha(p) for p in [Path(__file__),DRAFT/'opencc_resource_control.cpp',THIRD,SAFETY,LOOKUP,PLUGIN]},'variants':{},'steps':[]}
write(OUT/'report.json',report)
compiler=shutil.which(args.compiler) or args.compiler;cmake=shutil.which(args.cmake) or args.cmake;ninja=shutil.which('ninja');assert Path(compiler).is_file() and Path(cmake).is_file() and ninja
tool_inputs={name:{'path':path,'sha256':sha(Path(path).resolve()),'version':subprocess.check_output([path,'--version'],text=True).splitlines()[0]} for name,path in [('compiler',compiler),('cmake',cmake),('ninja',ninja)]}
write(OUT/'tools.json',tool_inputs)
def run(command,log,env=None,timeout=1200):
 started=time.monotonic()
 with log.open('w') as stream: result=subprocess.run(command,stdout=stream,stderr=subprocess.STDOUT,env=env,timeout=timeout)
 warnings=[{'line':number,'text':line} for number,line in enumerate(log.read_text().splitlines(),1) if re.search(r'(?i)(?:^\s*(?:CMake (?:Deprecation )?Warning\b|warning:|w:|WARNING\b)|:\s*warning\b|==[0-9]+==WARNING:)',line)]
 report['steps'].append({'argv':command,'returncode':result.returncode,'elapsed_seconds':time.monotonic()-started,'log':str(log),'log_sha256':sha(log),'warnings':warnings})
 write(OUT/'report.json',report)
 assert not warnings, 'Unexpected warning in original command log; no suppression or regrading'
 return result.returncode
def source_snapshot(directory):
 return {str(p.relative_to(directory)):sha(p) for p in sorted(directory.rglob('*'))
         if '.git' not in p.relative_to(directory).parts and p.is_file()}
# This baseline belongs to the real pinned source and never changes between copies.
source_baseline=source_snapshot(SOURCE)
assert source_baseline, 'Pinned OpenCC source is empty'
write(OUT/'source-baseline.json',source_baseline)
report['source_baseline_sha256']=sha(OUT/'source-baseline.json')
primary_failure=False
variant_sources={}
try:
 for variant in ['original','patched']:
  folder=OUT/variant;folder.mkdir();copy=folder/'OpenCC';shutil.copytree(SOURCE,copy,ignore=shutil.ignore_patterns('.git'))
  source_before={str(p.relative_to(copy)):sha(p) for p in copy.rglob('*') if p.is_file()};write(folder/'source-before.json',source_before)
  assert source_before==source_baseline, 'Copied OpenCC source differs from the immutable baseline'
  adapt=folder/'adapt.cmake'
  # OpenCC 1.4.2 already uses CMake 3.12 and host FindPython3; validate both
  # without rewriting upstream's current declarations or generation commands.
  adapt.write_text(f'include("{THIRD}")\ninclude("{SAFETY}")\ninclude("{LOOKUP}")\ninclude("{PLUGIN}")\ntrime_validate_opencc_cmake("{copy}")\ntrime_adapt_opencc_lookup("{copy}/src")\ntrime_adapt_opencc_plugin("{copy}/src")\n'+(f'trime_adapt_opencc_resources("{copy}/src")\n' if variant=='patched' else ''))
  assert run([cmake,'-P',str(adapt)],folder/'adapt.log',timeout=60)==0
  source_after={str(p.relative_to(copy)):sha(p) for p in copy.rglob('*') if p.is_file()};write(folder/'source-after.json',source_after)
  changed=[p for p in source_after if source_before[p]!=source_after[p]];assert set(changed)==({'src/Dict.hpp','src/TextDict.cpp','src/PrefixMatch.cpp','src/PluginSegmentation.cpp','src/MarisaDict.cpp','src/SerializableDict.hpp'} if variant=='patched' else {'src/Dict.hpp','src/TextDict.cpp','src/PrefixMatch.cpp','src/PluginSegmentation.cpp'})
  variant_sources[variant]=source_after
  shutil.copy2(DRAFT/'opencc_resource_control.cpp',folder/'resource_control.cpp')
  (folder/'CMakeLists.txt').write_text(f'''cmake_minimum_required(VERSION 3.10...3.31)
project(trime_opencc_resource_control LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
add_compile_options(-g -O1 -fno-omit-frame-pointer -fsanitize=address)
add_link_options(-fsanitize=address)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_GTEST OFF CACHE BOOL "" FORCE)
set(ENABLE_BENCHMARK OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(BUILD_OPENCC_JIEBA_PLUGIN OFF CACHE BOOL "" FORCE)
set(OPENCC_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(USE_SYSTEM_MARISA OFF CACHE BOOL "" FORCE)
# The copies have no .git: use the unmodified upstream1.4.2 archive fallback.
set(CMAKE_DISABLE_FIND_PACKAGE_Git TRUE)
add_subdirectory("{copy}" library)
# Match the existing production OpenccWorkarounds.cmake pointer-iterator API.
target_compile_definitions(libopencc PRIVATE RAPIDJSON_NOMEMBERITERATORCLASS)
add_executable(resource_control resource_control.cpp)
target_include_directories(resource_control PRIVATE "${{CMAKE_BINARY_DIR}}/library/src")
target_link_libraries(resource_control PRIVATE libopencc)
''')
  build=folder/'build'
  assert run([cmake,'-S',str(folder),'-B',str(build),'-G','Ninja','-DCMAKE_BUILD_TYPE=RelWithDebInfo','-DCMAKE_CXX_COMPILER='+compiler],folder/'configure.log',timeout=180)==0
  assert run([cmake,'--build',str(build),'--target','resource_control','--parallel',str(args.workers)],folder/'build.log',timeout=1200)==0
  exe=build/'resource_control';variant_report={'source_changes':changed,'source_map_before_sha256':sha(folder/'source-before.json'),'source_map_after_sha256':sha(folder/'source-after.json'),'executable_sha256':sha(exe),'scenarios':{}};report['variants'][variant]=variant_report
  env=dict(os.environ);env['ASAN_OPTIONS']='detect_leaks=1:halt_on_error=1:exitcode=86';env['LSAN_OPTIONS']='exitcode=87:report_objects=1'
  for scenario in ['direct-header','config-header','config-text','writer','filename-header','filename-text']:
   log=folder/(scenario+'.log');rc=run([str(exe),scenario,str(folder/('fixture-'+scenario))],log,env,timeout=60);raw=log.read_text()
   data=json.loads(next(line for line in raw.splitlines() if line.startswith('{')))
   assert data['attempts']==data['expected_exceptions']==data['successful_real_conversion_retries']==32 and data['descriptor_observer_calibrated']
   assert data['successful_filename_reader_retries']==(32 if scenario in ['filename-header','filename-text'] else 0)
   expected_handles=32 if variant=='original' and scenario in ['writer','filename-header','filename-text'] else 0
   assert data['retained_fixture_descriptors']==expected_handles
   assert data['retained_descriptors_after_each_failure']==(list(range(1,33)) if expected_handles else [0]*32)
   heap_failure=variant=='original' and scenario in ['direct-header','filename-header']
   if heap_failure:
    assert rc==87 and 'LeakSanitizer: detected memory leaks' in raw and '608 byte(s) leaked in 32 allocation(s)' in raw and 'MarisaDict::NewFromFile' in raw
   else:
    assert rc==(23 if expected_handles else 0) and 'LeakSanitizer: detected memory leaks' not in raw and 'ERROR: AddressSanitizer' not in raw
   variant_report['scenarios'][scenario]={'returncode':rc,'observed':data,'expected_negative_control':variant=='original' and (heap_failure or expected_handles!=0),'expected_header_lsan_bytes':608 if heap_failure else 0,'log_sha256':sha(log)}
   write(OUT/'report.json',report)
  assert all(sha(p)==report['inputs'][str(p)] for p in [Path(__file__),DRAFT/'opencc_resource_control.cpp',THIRD,SAFETY,LOOKUP,PLUGIN]),'Inputs changed during native controls'
 contrast=[path for path in variant_sources['original'] if variant_sources['original'][path]!=variant_sources['patched'][path]]
 assert set(contrast)=={'src/MarisaDict.cpp','src/SerializableDict.hpp'}, 'Only resource RAII may differ between negative and positive variants'
 report['resource_only_variant_delta']=contrast
 report['common_compatibility_sources']={name:variant_sources['original'][name] for name in ['src/Dict.hpp','src/TextDict.cpp','src/PrefixMatch.cpp','src/PluginSegmentation.cpp']}
 report['status']='completed';report['control_passed']=True
except BaseException as error:
 primary_failure=True
 report['status']='failed_or_incomplete';report['control_passed']=False;report['error']=repr(error)
 raise
finally:
 source_check_error=None
 try:
  source_final=source_snapshot(SOURCE)
  report['pinned_source_still_unchanged']=source_final==source_baseline
  write(OUT/'source-final.json',source_final)
  report['source_final_sha256']=sha(OUT/'source-final.json')
  report['tool_binaries_still_unchanged']=all(sha(Path(value['path']).resolve())==value['sha256'] for value in tool_inputs.values())
  if not report['tool_binaries_still_unchanged']:
   raise RuntimeError('Native control tool binary changed during execution')
 except Exception as error:
  source_check_error=error
  report['pinned_source_still_unchanged']=False
  report['source_check_error']=repr(error)
 if not report['pinned_source_still_unchanged']:
  report['status']='failed_or_incomplete';report['control_passed']=False
  report['source_integrity_error']='Pinned OpenCC source drifted or could not be verified'
 try:
  write(OUT/'report.json',report)
 except Exception:
  # A report write failure must not replace the original copy/build/test error.
  if not primary_failure:raise
 if not report['pinned_source_still_unchanged'] and not primary_failure:
  raise RuntimeError(report['source_integrity_error']) from source_check_error
print(json.dumps({'control_passed':report['control_passed'],'report':str(OUT/'report.json'),'sha256':sha(OUT/'report.json')},indent=2))
