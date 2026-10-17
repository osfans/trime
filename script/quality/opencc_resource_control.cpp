// SPDX-License-Identifier: GPL-3.0-or-later
// Real bundled OpenCC/Marisa resource control; no parser/allocator simulations.
#include <Config.hpp>
#include <Exception.hpp>
#include <MarisaDict.hpp>
#include <SerializableDict.hpp>
#include <SimpleConverter.hpp>
#include <TextDict.hpp>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace fs = std::filesystem;
using FileHandle = std::unique_ptr<FILE, decltype(&fclose)>;
void require(bool value, const std::string& message) {
  if (!value) throw std::runtime_error(message);
}
void writeFile(const fs::path& path, const std::string& bytes) {
  std::ofstream out(path, std::ios::binary | std::ios::trunc);
  out.write(bytes.data(), bytes.size());
  out.close();
  require(!out.fail(), "Cannot write fixture " + path.string());
}
std::string readFile(const fs::path& path) {
  std::ifstream input(path, std::ios::binary);
  require(input.good(), "Cannot read fixture");
  return {std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>()};
}
std::map<std::string, std::string> descriptors(const std::string& component,
                                             bool devFull = false) {
  std::map<std::string, std::string> result;
  for (const auto& item : fs::directory_iterator("/proc/self/fd")) {
    std::error_code error;
    const auto target = fs::read_symlink(item.path(), error).string();
    if (error == std::errc::no_such_file_or_directory) continue;
    require(!error, "Cannot inspect descriptor: " + error.message());
    if (target.find(component) != std::string::npos ||
        (devFull && target == "/dev/full"))
      result.emplace(item.path().filename().string(), target);
  }
  return result;
}
int execute(const std::string& scenario, const fs::path& directory) {
  require(fs::create_directory(directory), "Fixture directory already exists");
  const std::string component = "/" + directory.filename().string() + "/";
  const fs::path source = directory / "source.txt";
  const fs::path binary = directory / "dictionary.ocd2";
  const fs::path config = directory / "config.json";
  writeFile(source, "漢語\t汉语\n測試\t测试\n");
  auto text = opencc::SerializableDict::NewFromFile<opencc::TextDict>(source.string());
  auto dictionary = opencc::MarisaDict::NewFromDict(*text);
  const auto& serializable = static_cast<const opencc::SerializableDict&>(*dictionary);
  serializable.SerializeToFile(binary.string());
  const std::string complete = readFile(binary);
  const std::string header = "OPENCC_MARISA_0.2.5";
  require(header.size() == 19 && complete.substr(0, header.size()) == header,
          "Unexpected actual bundled dictionary format");
  auto writeConfig = [&](bool malformedText) {
    const std::string filename = (malformedText ? source : binary).string();
    require(filename.find_first_of("\"\\") == std::string::npos, "Unsafe JSON fixture path");
    const std::string dict = "{\"type\":\"" + std::string(malformedText ? "text" : "ocd2") +
                             "\",\"file\":\"" + filename + "\"}";
    writeFile(config, "{\"name\":\"resource control\",\"segmentation\":{\"type\":\"mmseg\",\"dict\":" +
                         dict + "},\"conversion_chain\":[{\"dict\":" + dict + "}]}");
  };
  const bool writer = scenario == "writer";
  require(descriptors(component, writer).empty(), "Unexpected fixture descriptor before calibration");
  {
    FileHandle observed(fopen((writer ? fs::path("/dev/full") : binary).c_str(), writer ? "wb" : "rb"), fclose);
    require(bool(observed), "Cannot open calibration handle");
    require(descriptors(component, writer).size() == 1, "Descriptor observer missed live fixture handle");
  }
  require(descriptors(component, writer).empty(), "Calibration handle remained open");
  writeConfig(false);
  std::vector<size_t> retained;
  int failures = 0, retries = 0;
  for (int i = 0; i < 32; ++i) {
    bool rejected = false;
    if (writer) {
      try {
        serializable.SerializeToFile(std::string("/dev/full"));
      } catch (const std::exception& error) {
        rejected = std::string(error.what()).find("MARISA_IO_ERROR") != std::string::npos;
      }
    } else {
      if (scenario == "config-text") {
        writeFile(source, "missing tab and value\n");
        writeConfig(true);
      } else {
        std::string bad = i % 2 == 0 ? complete.substr(0, header.size() - 1) : complete;
        if (i % 2 != 0) bad[0] = 'X';
        writeFile(binary, bad);
      }
      if (scenario == "direct-header") {
        FileHandle input(fopen(binary.c_str(), "rb"), fclose);
        require(bool(input), "Cannot open malformed fixture");
        try {
          opencc::MarisaDict::NewFromFile(input.get());
        } catch (const opencc::Exception& error) {
          rejected = std::string(error.what()).find("Invalid OpenCC dictionary header") != std::string::npos;
        }
      } else {
        try {
          opencc::SimpleConverter converter(config.string());
          converter.Convert("漢語測試");
        } catch (const std::runtime_error& error) {
          const auto expected = scenario == "config-text" ? "Tabular not found" : "Invalid OpenCC dictionary header";
          rejected = std::string(error.what()).find(expected) != std::string::npos;
        }
      }
    }
    require(rejected, "Actual parser/writer did not produce expected exception");
    ++failures;
    retained.push_back(descriptors(component, writer).size());
    writeFile(binary, complete);
    writeFile(source, "漢語\t汉语\n測試\t测试\n");
    writeConfig(false);
    {
      opencc::SimpleConverter recovered(config.string());
      require(recovered.Convert("漢語測試") == "汉语测试", "Real conversion retry failed");
    }
    if (writer) {
      const fs::path retry = directory / "writer-retry.ocd2";
      serializable.SerializeToFile(retry.string());
      require(readFile(retry) == complete, "Valid serialization retry changed bytes");
    }
    require(descriptors(component, writer).size() == retained.back(), "Valid retry changed retained-handle count");
    ++retries;
  }
  const auto remaining = descriptors(component, writer);
  std::cout << "{\"scenario\":\"" << scenario << "\",\"attempts\":32,\"expected_exceptions\":" << failures
            << ",\"successful_real_conversion_retries\":" << retries << ",\"descriptor_observer_calibrated\":true,\"retained_descriptors_after_each_failure\":[";
  for (size_t i = 0; i < retained.size(); ++i) std::cout << (i ? "," : "") << retained[i];
  std::cout << "],\"retained_fixture_descriptors\":" << remaining.size()
            << ",\"descriptor_resources_clean\":" << (remaining.empty() ? "true" : "false")
            << ",\"header_size_bytes\":19,\"native_heap_check\":\"external_exit_lsan_required\"}" << std::endl;
  // Keep fixtures and leaked original handles as evidence until process exit.
  // LSAN independently reports the original header allocation; it overrides exit status.
  return remaining.empty() ? 0 : 23;
}
int main(int argc, char** argv) {
  try {
    require(argc == 3, "Expected scenario and new owned fixture directory");
    const std::string scenario = argv[1];
    require(scenario == "direct-header" || scenario == "config-header" ||
            scenario == "config-text" || scenario == "writer", "Unknown scenario");
    return execute(scenario, fs::absolute(argv[2]));
  } catch (const opencc::Exception& error) {
    std::cerr << "UNEXPECTED OPENCC FAILURE: " << error.what() << std::endl;
  } catch (const std::exception& error) {
    std::cerr << "UNEXPECTED CONTROL FAILURE: " << error.what() << std::endl;
  }
  return 2;
}
