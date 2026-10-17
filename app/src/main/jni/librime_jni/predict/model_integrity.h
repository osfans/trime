// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>

namespace trime::prediction {

// Small SHA-256 with fixed working arrays and a 64-character result for
// checking the private model image before parsing. The caller bounds its size.
inline std::string ModelSha256(const unsigned char* data, std::size_t size) {
  constexpr std::array<std::uint32_t, 64> constants{{
      0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
      0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
      0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
      0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
      0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
      0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
      0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
      0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2}};
  std::array<std::uint32_t, 8> hash{{0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
                                   0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19}};
  const auto rotate = [](std::uint32_t value, int count) {
    return (value >> count) | (value << (32 - count));
  };
  const std::size_t blocks = (size + 9 + 63) / 64;
  const std::uint64_t bits = static_cast<std::uint64_t>(size) * 8;
  for (std::size_t block = 0; block < blocks; ++block) {
    std::array<std::uint32_t, 64> words{};
    for (std::size_t i = 0; i < 64; ++i) {
      const std::size_t index = block * 64 + i;
      unsigned char byte = 0;
      if (index < size) byte = data[index];
      else if (index == size) byte = 0x80;
      else if (index >= blocks * 64 - 8)
        byte = static_cast<unsigned char>(bits >> ((blocks * 64 - 1 - index) * 8));
      words[i / 4] |= static_cast<std::uint32_t>(byte) << ((3 - i % 4) * 8);
    }
    for (std::size_t i = 16; i < 64; ++i) {
      const auto s0 = rotate(words[i-15],7) ^ rotate(words[i-15],18) ^ (words[i-15] >> 3);
      const auto s1 = rotate(words[i-2],17) ^ rotate(words[i-2],19) ^ (words[i-2] >> 10);
      words[i] = words[i-16] + s0 + words[i-7] + s1;
    }
    auto state = hash;
    for (std::size_t i = 0; i < 64; ++i) {
      const auto s1 = rotate(state[4],6) ^ rotate(state[4],11) ^ rotate(state[4],25);
      const auto choose = (state[4] & state[5]) ^ (~state[4] & state[6]);
      const auto t1 = state[7] + s1 + choose + constants[i] + words[i];
      const auto s0 = rotate(state[0],2) ^ rotate(state[0],13) ^ rotate(state[0],22);
      const auto majority = (state[0] & state[1]) ^ (state[0] & state[2]) ^ (state[1] & state[2]);
      const auto t2 = s0 + majority;
      state = {{t1+t2,state[0],state[1],state[2],state[3]+t1,state[4],state[5],state[6]}};
    }
    for (std::size_t i = 0; i < hash.size(); ++i) hash[i] += state[i];
  }
  constexpr char digits[] = "0123456789abcdef";
  std::string result;
  result.reserve(64);
  for (auto word : hash) {
    for (int shift = 28; shift >= 0; shift -= 4)
      result += digits[(word >> shift) & 15];
  }
  return result;
}

}  // namespace trime::prediction
