// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/*
 * Copyright 2021 M.Vokhmentsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore;

/**
 * This class is for internal usage only and should not be exposed externally.
 * @hide
 */
public class QuadrupleBuilder {
  public static QuadrupleBuilder parseDecimal(byte[] digits, int exp10) {
    QuadrupleBuilder q = new QuadrupleBuilder();
    q.parse(digits, exp10);
    return q;
  }

  // The fields containing the value of the instance
  public int exponent;
  public long mantHi;
  public long mantLo;
  // 2^192 = 6.277e57, so the 58-th digit after point may affect the result
  static final int MAX_MANTISSA_LENGTH = 59;
  // Max value of the decimal exponent, corresponds to EXPONENT_OF_MAX_VALUE
  static final int MAX_EXP10 = 646456993;
  // Min value of the decimal exponent, corresponds to EXPONENT_OF_MIN_NORMAL
  static final int MIN_EXP10 = -646457032;
  // (2^63) / 10 =~ 9.223372e17
  static final double TWO_POW_63_DIV_10 = 922337203685477580.0;
  // Just for convenience: 0x8000_0000_0000_0000L
  static final long HIGH_BIT = 0x8000000000000000L;
  // Just for convenience: 0x8000_0000L, 2^31
  static final double POW_2_31 = 2147483648.0;
  // Just for convenience: 0x0000_0000_FFFF_FFFFL
  static final long LOWER_32_BITS = 0x00000000FFFFFFFFL;
  // Just for convenience: 0xFFFF_FFFF_0000_0000L;
  static final long HIGHER_32_BITS = 0xFFFFFFFF00000000L;
  // Approximate value of log<sub>2</sub>(10)
  static final double LOG2_10 = Math.log(10) / Math.log(2);
  // Approximate value of log<sub>2</sub>(e)
  static final double LOG2_E = 1 / Math.log(2.0);
  // The value of the exponent (biased) corresponding to {@code 1.0 == 2^0}; equals to 2_147_483_647
  // ({@code 0x7FFF_FFFF}).
  static final int EXPONENT_BIAS = 0x7FFF_FFFF;
  // The value of the exponent (biased), corresponding to {@code Infinity}, {@code _Infinty}, and
  // {@code NaN}
  static final long EXPONENT_OF_INFINITY = 0xFFFFFFFFL;
  // An array of positive powers of two, each value consists of 4 longs: decimal exponent and 3 x 64
  // bits of mantissa, divided by ten Used to find an arbitrary power of 2 (by powerOfTwo(long exp))
  private static final long[][] POS_POWERS_OF_2 = { // 0: 2^0 =   1 = 0.1e1
    {
      1, 0x1999_9999_9999_9999L, 0x9999_9999_9999_9999L, 0x9999_9999_9999_999aL
    }, // 1: 2^(2^0) =   2^1 =   2 = 0.2e1
    {1, 0x3333_3333_3333_3333L, 0x3333_3333_3333_3333L, 0x3333_3333_3333_3334L}, // ***
    // 2: 2^(2^1) =   2^2 =   4 = 0.4e1
    {1, 0x6666_6666_6666_6666L, 0x6666_6666_6666_6666L, 0x6666_6666_6666_6667L}, // ***
    // 3: 2^(2^2) =   2^4 =   16 = 0.16e2
    {2, 0x28f5_c28f_5c28_f5c2L, 0x8f5c_28f5_c28f_5c28L, 0xf5c2_8f5c_28f5_c290L}, // ***
    // 4: 2^(2^3) =   2^8 =   256 = 0.256e3
    {3, 0x4189_374b_c6a7_ef9dL, 0xb22d_0e56_0418_9374L, 0xbc6a_7ef9_db22_d0e6L}, // ***
    // 5: 2^(2^4) =   2^16 =   65536 = 0.65536e5
    {
      5, 0xa7c5_ac47_1b47_8423L, 0x0fcf_80dc_3372_1d53L, 0xcddd_6e04_c059_2104L
    }, // 6: 2^(2^5) =   2^32 =   4294967296 = 0.4294967296e10
    {
      10, 0x6df3_7f67_5ef6_eadfL, 0x5ab9_a207_2d44_268dL, 0x97df_837e_6748_956eL
    }, // 7: 2^(2^6) =   2^64 =   18446744073709551616 = 0.18446744073709551616e20
    {
      20, 0x2f39_4219_2484_46baL, 0xa23d_2ec7_29af_3d61L, 0x0607_aa01_67dd_94cbL
    }, // 8: 2^(2^7) =   2^128 =   340282366920938463463374607431768211456 =
    // 0.340282366920938463463374607431768211456e39
    {
      39, 0x571c_bec5_54b6_0dbbL, 0xd5f6_4baf_0506_840dL, 0x451d_b70d_5904_029bL
    }, // 9: 2^(2^8) =   2^256 =
    // 1.1579208923731619542357098500868790785326998466564056403945758401E+77 =
    // 0.11579208923731619542357098500868790785326998466564056403945758401e78
    {78, 0x1da4_8ce4_68e7_c702L, 0x6520_247d_3556_476dL, 0x1469_caf6_db22_4cfaL}, // ***
    // 10: 2^(2^9) =   2^512 =
    // 1.3407807929942597099574024998205846127479365820592393377723561444E+154 =
    // 0.13407807929942597099574024998205846127479365820592393377723561444e155
    {
      155, 0x2252_f0e5_b397_69dcL, 0x9ae2_eea3_0ca3_ade0L, 0xeeaa_3c08_dfe8_4e30L
    }, // 11: 2^(2^10) =   2^1024 =
    // 1.7976931348623159077293051907890247336179769789423065727343008116E+308 =
    // 0.17976931348623159077293051907890247336179769789423065727343008116e309
    {
      309, 0x2e05_5c9a_3f6b_a793L, 0x1658_3a81_6eb6_0a59L, 0x22c4_b082_6cf1_ebf7L
    }, // 12: 2^(2^11) =   2^2048 =
    // 3.2317006071311007300714876688669951960444102669715484032130345428E+616 =
    // 0.32317006071311007300714876688669951960444102669715484032130345428e617
    {
      617, 0x52bb_45e9_cf23_f17fL, 0x7688_c076_06e5_0364L, 0xb344_79aa_9d44_9a57L
    }, // 13: 2^(2^12) =   2^4096 =
    // 1.0443888814131525066917527107166243825799642490473837803842334833E+1233 =
    // 0.10443888814131525066917527107166243825799642490473837803842334833e1234
    {
      1234, 0x1abc_81c8_ff5f_846cL, 0x8f5e_3c98_53e3_8c97L, 0x4506_0097_f3bf_9296L
    }, // 14: 2^(2^13) =   2^8192 =
    // 1.0907481356194159294629842447337828624482641619962326924318327862E+2466 =
    // 0.10907481356194159294629842447337828624482641619962326924318327862e2467
    {
      2467, 0x1bec_53b5_10da_a7b4L, 0x4836_9ed7_7dbb_0eb1L, 0x3b05_587b_2187_b41eL
    }, // 15: 2^(2^14) =   2^16384 =
    // 1.1897314953572317650857593266280071307634446870965102374726748212E+4932 =
    // 0.11897314953572317650857593266280071307634446870965102374726748212e4933
    {
      4933, 0x1e75_063a_5ba9_1326L, 0x8abf_b8e4_6001_6ae3L, 0x2800_8702_d29e_8a3cL
    }, // 16: 2^(2^15) =   2^32768 =
    // 1.4154610310449547890015530277449516013481307114723881672343857483E+9864 =
    // 0.14154610310449547890015530277449516013481307114723881672343857483e9865
    {
      9865, 0x243c_5d8b_b5c5_fa55L, 0x40c6_d248_c588_1915L, 0x4c0f_d99f_d5be_fc22L
    }, // 17: 2^(2^16) =   2^65536 =
    // 2.0035299304068464649790723515602557504478254755697514192650169737E+19728 =
    // 0.20035299304068464649790723515602557504478254755697514192650169737e19729
    {
      19729, 0x334a_5570_c3f4_ef3cL, 0xa13c_36c4_3f97_9c90L, 0xda7a_c473_555f_b7a8L
    }, // 18: 2^(2^17) =   2^131072 =
    // 4.0141321820360630391660606060388767343771510270414189955825538065E+39456 =
    // 0.40141321820360630391660606060388767343771510270414189955825538065e39457
    {
      39457, 0x66c3_0444_5dd9_8f3bL, 0xa8c2_93a2_0e47_a41bL, 0x4c5b_03dc_1260_4964L
    }, // 19: 2^(2^18) =   2^262144 =
    // 1.6113257174857604736195721184520050106440238745496695174763712505E+78913 =
    // 0.16113257174857604736195721184520050106440238745496695174763712505e78914
    {
      78914, 0x293f_fbf5_fb02_8cc4L, 0x89d3_e5ff_4423_8406L, 0x369a_339e_1bfe_8c9bL
    }, // 20: 2^(2^19) =   2^524288 =
    // 2.5963705678310007761265964957268828277447343763484560463573654868E+157826 =
    // 0.25963705678310007761265964957268828277447343763484560463573654868e157827
    {
      157827, 0x4277_92fb_b68e_5d20L, 0x7b29_7cd9_fc15_4b62L, 0xf091_4211_4aa9_a20cL
    }, // 21: 2^(2^20) =   2^1048576 =
    // 6.7411401254990734022690651047042454376201859485326882846944915676E+315652 =
    // 0.67411401254990734022690651047042454376201859485326882846944915676e315653
    {
      315653, 0xac92_bc65_ad5c_08fcL, 0x00be_eb11_5a56_6c19L, 0x4ba8_82d8_a462_2437L
    }, // 22: 2^(2^21) =   2^2097152 =
    // 4.5442970191613663099961595907970650433180103994591456270882095573E+631305 =
    // 0.45442970191613663099961595907970650433180103994591456270882095573e631306
    {
      631306, 0x7455_8144_0f92_e80eL, 0x4da8_22cf_7f89_6f41L, 0x509d_5986_7816_4ecdL
    }, // 23: 2^(2^22) =   2^4194304 =
    // 2.0650635398358879243991194945816501695274360493029670347841664177E+1262611 =
    // 0.20650635398358879243991194945816501695274360493029670347841664177e1262612
    {
      1262612, 0x34dd_99b4_c695_23a5L, 0x64bc_2e8f_0d8b_1044L, 0xb03b_1c96_da5d_d349L
    }, // 24: 2^(2^23) =   2^8388608 =
    // 4.2644874235595278724327289260856157547554200794957122157246170406E+2525222 =
    // 0.42644874235595278724327289260856157547554200794957122157246170406e2525223
    {
      2525223, 0x6d2b_bea9_d6d2_5a08L, 0xa0a4_606a_88e9_6b70L, 0x1820_63bb_c2fe_8520L
    }, // 25: 2^(2^24) =   2^16777216 =
    // 1.8185852985697380078927713277749906189248596809789408311078112486E+5050445 =
    // 0.18185852985697380078927713277749906189248596809789408311078112486e5050446
    {
      5050446, 0x2e8e_47d6_3bfd_d6e3L, 0x2b55_fa89_76ea_a3e9L, 0x1a6b_9d30_8641_2a73L
    }, // 26: 2^(2^25) =   2^33554432 =
    // 3.3072524881739831340558051919726975471129152081195558970611353362E+10100890 =
    // 0.33072524881739831340558051919726975471129152081195558970611353362e10100891
    {
      10100891, 0x54aa_68ef_a1d7_19dfL, 0xd850_5806_612c_5c8fL, 0xad06_8837_fee8_b43aL
    }, // 27: 2^(2^26) =   2^67108864 =
    // 1.0937919020533002449982468634925923461910249420785622990340704603E+20201781 =
    // 0.10937919020533002449982468634925923461910249420785622990340704603e20201782
    {
      20201782, 0x1c00_464c_cb7b_ae77L, 0x9e38_7778_4c77_982cL, 0xd94a_f3b6_1717_404fL
    }, // 28: 2^(2^27) =   2^134217728 =
    // 1.1963807249973763567102377630870670302911237824129274789063323723E+40403562 =
    // 0.11963807249973763567102377630870670302911237824129274789063323723e40403563
    {
      40403563, 0x1ea0_99c8_be2b_6cd0L, 0x8bfb_6d53_9fa5_0466L, 0x6d3b_c37e_69a8_4218L
    }, // 29: 2^(2^28) =   2^268435456 =
    // 1.4313268391452478724777126233530788980596273340675193575004129517E+80807124 =
    // 0.14313268391452478724777126233530788980596273340675193575004129517e80807125
    {
      80807125, 0x24a4_57f4_66ce_8d18L, 0xf2c8_f3b8_1bc6_bb59L, 0xa78c_7576_92e0_2d49L
    }, // 30: 2^(2^29) =   2^536870912 =
    // 2.0486965204575262773910959587280218683219330308711312100181276813E+161614248 =
    // 0.20486965204575262773910959587280218683219330308711312100181276813e161614249
    {
      161614249, 0x3472_5667_7aba_6b53L, 0x3fbf_90d3_0611_a67cL, 0x1e03_9d87_e0bd_b32bL
    }, // 31: 2^(2^30) =   2^1073741824 =
    // 4.1971574329347753848087162337676781412761959309467052555732924370E+323228496 =
    // 0.41971574329347753848087162337676781412761959309467052555732924370e323228497
    {
      323228497, 0x6b72_7daf_0fd3_432aL, 0x71f7_1121_f9e4_200fL, 0x8fcd_9942_d486_c10cL
    }, // 32: 2^(2^31) =   2^2147483648 =
    // 1.7616130516839633532074931497918402856671115581881347960233679023E+646456993 =
    // 0.17616130516839633532074931497918402856671115581881347960233679023e646456994
    {646456994, 0x2d18_e844_84d9_1f78L, 0x4079_bfe7_829d_ec6fL, 0x2155_1643_e365_abc6L}
  };
  // An array of negative powers of two, each value consists of 4 longs: decimal exponent and 3 x 64
  // bits of mantissa, divided by ten. Used to find an arbitrary power of 2 (by powerOfTwo(long
  // exp))
  private static final long[][] NEG_POWERS_OF_2 = { // v18
    // 0: 2^0 =   1 = 0.1e1
    {
      1, 0x1999_9999_9999_9999L, 0x9999_9999_9999_9999L, 0x9999_9999_9999_999aL
    }, // 1: 2^-(2^0) =   2^-1 =   0.5 = 0.5e0
    {
      0, 0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L
    }, // 2: 2^-(2^1) =   2^-2 =   0.25 = 0.25e0
    //      {0, 0x4000_0000_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L},
    {0, 0x4000_0000_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0001L}, // ***
    // 3: 2^-(2^2) =   2^-4 =   0.0625 = 0.625e-1
    {
      -1, 0xa000_0000_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L
    }, // 4: 2^-(2^3) =   2^-8 =   0.00390625 = 0.390625e-2
    {
      -2, 0x6400_0000_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L
    }, // 5: 2^-(2^4) =   2^-16 =   0.0000152587890625 = 0.152587890625e-4
    {-4, 0x2710_0000_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0001L}, // ***
    // 6: 2^-(2^5) =   2^-32 =   2.3283064365386962890625E-10 = 0.23283064365386962890625e-9
    {-9, 0x3b9a_ca00_0000_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0001L}, // ***
    // 7: 2^-(2^6) =   2^-64 =   5.42101086242752217003726400434970855712890625E-20 =
    // 0.542101086242752217003726400434970855712890625e-19
    {
      -19, 0x8ac7_2304_89e8_0000L, 0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L
    }, // 8: 2^-(2^7) =   2^-128 =
    // 2.9387358770557187699218413430556141945466638919302188037718792657E-39 =
    // 0.29387358770557187699218413430556141945466638919302188037718792657e-38
    {-38, 0x4b3b_4ca8_5a86_c47aL, 0x098a_2240_0000_0000L, 0x0000_0000_0000_0001L}, // ***
    // 9: 2^-(2^8) =   2^-256 =
    // 8.6361685550944446253863518628003995711160003644362813850237034700E-78 =
    // 0.86361685550944446253863518628003995711160003644362813850237034700e-77
    {
      -77, 0xdd15_fe86_affa_d912L, 0x49ef_0eb7_13f3_9ebeL, 0xaa98_7b6e_6fd2_a002L
    }, // 10: 2^-(2^9) =   2^-512 =
    // 7.4583407312002067432909653154629338373764715346004068942715183331E-155 =
    // 0.74583407312002067432909653154629338373764715346004068942715183331e-154
    {
      -154, 0xbeee_fb58_4aff_8603L, 0xaafb_550f_facf_d8faL, 0x5ca4_7e4f_88d4_5371L
    }, // 11: 2^-(2^10) =   2^-1024 =
    // 5.5626846462680034577255817933310101605480399511558295763833185421E-309 =
    // 0.55626846462680034577255817933310101605480399511558295763833185421e-308
    {-308, 0x8e67_9c2f_5e44_ff8fL, 0x570f_09ea_a7ea_7648L, 0x5961_db50_c6d2_b888L}, // ***
    // 12: 2^-(2^11) =   2^-2048 =
    // 3.0943460473825782754801833699711978538925563038849690459540984582E-617 =
    // 0.30943460473825782754801833699711978538925563038849690459540984582e-616
    {
      -616, 0x4f37_1b33_99fc_2ab0L, 0x8170_041c_9feb_05aaL, 0xc7c3_4344_7c75_bcf6L
    }, // 13: 2^-(2^12) =   2^-4096 =
    // 9.5749774609521853579467310122804202420597417413514981491308464986E-1234 =
    // 0.95749774609521853579467310122804202420597417413514981491308464986e-1233
    {
      -1233, 0xf51e_9281_7901_3fd3L, 0xde4b_d12c_de4d_985cL, 0x4a57_3ca6_f94b_ff14L
    }, // 14: 2^-(2^13) =   2^-8192 =
    // 9.1680193377742358281070619602424158297818248567928361864131947526E-2467 =
    // 0.91680193377742358281070619602424158297818248567928361864131947526e-2466
    {
      -2466, 0xeab3_8812_7bcc_aff7L, 0x1667_6391_42b9_fbaeL, 0x775e_c999_5e10_39fbL
    }, // 15: 2^-(2^14) =   2^-16384 =
    // 8.4052578577802337656566945433043815064951983621161781002720680748E-4933 =
    // 0.84052578577802337656566945433043815064951983621161781002720680748e-4932
    {
      -4932, 0xd72c_b2a9_5c7e_f6ccL, 0xe81b_f1e8_25ba_7515L, 0xc2fe_b521_d6cb_5dcdL
    }, // 16: 2^-(2^15) =   2^-32768 =
    // 7.0648359655776364427774021878587184537374439102725065590941425796E-9865 =
    // 0.70648359655776364427774021878587184537374439102725065590941425796e-9864
    {-9864, 0xb4dc_1be6_6045_02dcL, 0xd491_079b_8eef_6535L, 0x578d_3965_d24d_e84dL}, // ***
    // 17: 2^-(2^16) =   2^-65536 =
    // 4.9911907220519294656590574792132451973746770423207674161425040336E-19729 =
    // 0.49911907220519294656590574792132451973746770423207674161425040336e-19728
    {-19728, 0x7fc6_447b_ee60_ea43L, 0x2548_da5c_8b12_5b27L, 0x5f42_d114_2f41_d349L}, // ***
    // 18: 2^-(2^17) =   2^-131072 =
    // 2.4911984823897261018394507280431349807329035271689521242878455599E-39457 =
    // 0.24911984823897261018394507280431349807329035271689521242878455599e-39456
    {-39456, 0x3fc6_5180_f88a_f8fbL, 0x6a69_15f3_8334_9413L, 0x063c_3708_b6ce_b291L}, // ***
    // 19: 2^-(2^18) =   2^-262144 =
    // 6.2060698786608744707483205572846793091942192651991171731773832448E-78914 =
    // 0.62060698786608744707483205572846793091942192651991171731773832448e-78913
    {
      -78913, 0x9ee0_197c_8dcd_55bfL, 0x2b2b_9b94_2c38_f4a2L, 0x0f8b_a634_e9c7_06aeL
    }, // 20: 2^-(2^19) =   2^-524288 =
    // 3.8515303338821801176537443725392116267291403078581314096728076497E-157827 =
    // 0.38515303338821801176537443725392116267291403078581314096728076497e-157826
    {-157826, 0x6299_63a2_5b8b_2d79L, 0xd00b_9d22_86f7_0876L, 0xe970_0470_0c36_44fcL}, // ***
    // 21: 2^-(2^20) =   2^-1048576 =
    // 1.4834285912814577854404052243709225888043963245995136935174170977E-315653 =
    // 0.14834285912814577854404052243709225888043963245995136935174170977e-315652
    {
      -315652, 0x25f9_cc30_8cee_f4f3L, 0x40f1_9543_911a_4546L, 0xa2cd_3894_52cf_c366L
    }, // 22: 2^-(2^21) =   2^-2097152 =
    // 2.2005603854312903332428997579002102976620485709683755186430397089E-631306 =
    // 0.22005603854312903332428997579002102976620485709683755186430397089e-631305
    {
      -631305, 0x3855_97b0_d47e_76b8L, 0x1b9f_67e1_03bf_2329L, 0xc311_9848_5959_85f7L
    }, // 23: 2^-(2^22) =   2^-4194304 =
    // 4.8424660099295090687215589310713586524081268589231053824420510106E-1262612 =
    // 0.48424660099295090687215589310713586524081268589231053824420510106e-1262611
    {-1262611, 0x7bf7_95d2_76c1_2f66L, 0x66a6_1d62_a446_659aL, 0xa1a4_d73b_ebf0_93d5L}, // ***
    // 24: 2^-(2^23) =   2^-8388608 =
    // 2.3449477057322620222546775527242476219043877555386221929831430440E-2525223 =
    // 0.23449477057322620222546775527242476219043877555386221929831430440e-2525222
    {-2525222, 0x3c07_d96a_b1ed_7799L, 0xcb73_55c2_2cc0_5ac0L, 0x4ffc_0ab7_3b1f_6a49L}, // ***
    // 25: 2^-(2^24) =   2^-16777216 =
    // 5.4987797426189993226257377747879918011694025935111951649826798628E-5050446 =
    // 0.54987797426189993226257377747879918011694025935111951649826798628e-5050445
    {-5050445, 0x8cc4_cd8c_3ede_fb9aL, 0x6c8f_f86a_90a9_7e0cL, 0x166c_fddb_f98b_71bfL}, // ***
    // 26: 2^-(2^25) =   2^-33554432 =
    // 3.0236578657837068435515418409027857523343464783010706819696074665E-10100891 =
    // 0.30236578657837068435515418409027857523343464783010706819696074665e-10100890
    {-10100890, 0x4d67_d81c_c88e_1228L, 0x1d7c_fb06_666b_79b3L, 0x7b91_6728_aaa4_e70dL}, // ***
    // 27: 2^-(2^26) =   2^-67108864 =
    // 9.1425068893156809483320844568740945600482370635012633596231964471E-20201782 =
    // 0.91425068893156809483320844568740945600482370635012633596231964471e-20201781
    {-20201781, 0xea0c_5549_4e7a_552dL, 0xb88c_b948_4bb8_6c61L, 0x8d44_893c_610b_b7dFL}, // ***
    // 28: 2^-(2^27) =   2^-134217728 =
    // 8.3585432221184688810803924874542310018191301711943564624682743545E-40403563 =
    // 0.83585432221184688810803924874542310018191301711943564624682743545e-40403562
    {
      -40403562, 0xd5fa_8c82_1ec0_c24aL, 0xa80e_46e7_64e0_f8b0L, 0xa727_6bfa_432f_ac7eL
    }, // 29: 2^-(2^28) =   2^-268435456 =
    // 6.9865244796022595809958912202005005328020601847785697028605460277E-80807125 =
    // 0.69865244796022595809958912202005005328020601847785697028605460277e-80807124
    {
      -80807124, 0xb2da_e307_426f_6791L, 0xc970_b82f_58b1_2918L, 0x0472_592f_7f39_190eL
    }, // 30: 2^-(2^29) =   2^-536870912 =
    // 4.8811524304081624052042871019605298977947353140996212667810837790E-161614249 =
    // 0.48811524304081624052042871019605298977947353140996212667810837790e-161614248
    //      {-161614248, 0x7cf5_1edd_8a15_f1c9L, 0x656d_ab34_98f8_e697L, 0x12da_a2a8_0e53_c809L},
    {
      -161614248, 0x7cf5_1edd_8a15_f1c9L, 0x656d_ab34_98f8_e697L, 0x12da_a2a8_0e53_c807L
    }, // 31: 2^-(2^30) =   2^-1073741824 =
    // 2.3825649048879510732161697817326745204151961255592397879550237608E-323228497 =
    // 0.23825649048879510732161697817326745204151961255592397879550237608e-323228496
    {
      -323228496, 0x3cfe_609a_b588_3c50L, 0xbec8_b5d2_2b19_8871L, 0xe184_7770_3b46_22b4L
    }, // 32: 2^-(2^31) =   2^-2147483648 =
    // 5.6766155260037313438164181629489689531186932477276639365773003794E-646456994 =
    // 0.56766155260037313438164181629489689531186932477276639365773003794e-646456993
    {-646456993, 0x9152_447b_9d7c_da9aL, 0x3b4d_3f61_10d7_7aadL, 0xfa81_bad1_c394_adb4L}
  };
  // Buffers used internally
  // The order of words in the arrays is big-endian: the highest part is in buff[0] (in buff[1] for
  // buffers of 10 words)

  private final long[] buffer4x64B = new long[4];
  private final long[] buffer6x32A = new long[6];
  private final long[] buffer6x32B = new long[6];
  private final long[] buffer6x32C = new long[6];
  private final long[] buffer12x32 = new long[12];

  private void parse(byte[] digits, int exp10) {
    exp10 += (digits).length - 1; // digits is viewed as x.yyy below.
    this.exponent = 0;
    this.mantHi = 0L;
    this.mantLo = 0L;
    // Finds numeric value of the decimal mantissa
    long[] mantissa = this.buffer6x32C;
    int exp10Corr = parseMantissa(digits, mantissa);
    if (exp10Corr == 0 && isEmpty(mantissa)) {
      // Mantissa == 0
      return;
    }
    // takes account of the point position in the mant string and possible carry as a result of
    // round-up (like 9.99e1 -> 1.0e2)
    exp10 += exp10Corr;
    if (exp10 < MIN_EXP10) {
      return;
    }
    if (exp10 > MAX_EXP10) {
      this.exponent = ((int) (long) (EXPONENT_OF_INFINITY));
      return;
    }
    double exp2 = findBinaryExponent(exp10, mantissa);
    // Finds binary mantissa and possible exponent correction. Fills the fields.
    findBinaryMantissa(exp10, exp2, mantissa);
  }

  private int parseMantissa(byte[] digits, long[] mantissa) {
    for (int i = (0); i < (6); i++) {
      mantissa[i] = 0L;
    }
    // Skip leading zeroes
    int firstDigit = 0;
    while (firstDigit < (digits).length && digits[firstDigit] == 0) {
      firstDigit += 1;
    }
    if (firstDigit == (digits).length) {
      return 0; // All zeroes
    }
    int expCorr = -firstDigit;
    // Limit the string length to avoid unnecessary fuss
    if ((digits).length - firstDigit > MAX_MANTISSA_LENGTH) {
      boolean carry = digits[MAX_MANTISSA_LENGTH] >= 5; // The highest digit to be truncated
      byte[] truncated = new byte[MAX_MANTISSA_LENGTH];
      ;
      for (int i = (0); i < (MAX_MANTISSA_LENGTH); i++) {
        truncated[i] = digits[i + firstDigit];
      }
      if (carry) { // Round-up: add carry
        expCorr += addCarry(truncated); // May add an extra digit in front of it (99..99 -> 100)
      }
      digits = truncated;
      firstDigit = 0;
    }
    for (int i = ((digits).length) - 1; i >= (firstDigit); i--) { // digits, starting from the last
      mantissa[0] |= ((long) (digits[i])) << 32L;
      divBuffBy10(mantissa);
    }
    return expCorr;
  }

  // Divides the unpacked value stored in the given buffer by 10
  // @param buffer contains the unpacked value to divide (32 least significant bits are used)
  private void divBuffBy10(long[] buffer) {
    int maxIdx = (buffer).length;
    // big/endian
    for (int i = (0); i < (maxIdx); i++) {
      long r = buffer[i] % 10L;
      buffer[i] = ((buffer[i]) / (10L));
      if (i + 1 < maxIdx) {
        buffer[i + 1] += r << 32L;
      }
    }
  }

  // Checks if the buffer is empty (contains nothing but zeros)
  // @param buffer the buffer to check
  // @return {@code true} if the buffer is empty, {@code false} otherwise
  private boolean isEmpty(long[] buffer) {
    for (int i = (0); i < ((buffer).length); i++) {
      if (buffer[i] != 0L) {
        return false;
      }
    }
    return true;
  }

  // Adds one to a decimal number represented as a sequence of decimal digits. propagates carry as
  // needed, so that {@code addCarryTo("6789") = "6790", addCarryTo("9999") = "10000"} etc.
  // @return 1 if an additional higher "1" was added in front of the number as a result of
  //     rounding-up, 0 otherwise
  private int addCarry(byte[] digits) {
    for (int i = ((digits).length) - 1; i >= (0); i--) { // starting with the lowest digit
      byte c = digits[i];
      if (c == 9) {
        digits[i] = 0;
      } else {
        digits[i] = ((byte) (digits[i] + 1));
        return 0;
      }
    }
    digits[0] = 1;
    return 1;
  }

  // Finds binary exponent, using decimal exponent and mantissa.<br>
  // exp2 = exp10 * log<sub>2</sub>(10) + log<sub>2</sub>(mant)<br>
  // @param exp10 decimal exponent
  // @param mantissa array of longs containing decimal mantissa (divided by 10)
  // @return found value of binary exponent
  private double findBinaryExponent(int exp10, long[] mantissa) {
    long mant10 =
        mantissa[0] << 31L | ((mantissa[1]) >>> (1L)); // Higher 63 bits of the mantissa, in range
    // 0x0CC..CCC -- 0x7FF..FFF (2^63/10 -- 2^63-1)
    // decimal value of the mantissa in range 1.0..9.9999...
    double mant10d = ((double) (mant10)) / TWO_POW_63_DIV_10;
    return ((long) Math.floor(((double) (exp10)) * LOG2_10 + log2(mant10d))); // Binary exponent
  }

  // Calculates log<sub>2</sub> of the given x
  // @param x argument that can't be 0
  // @return the value of log<sub>2</sub>(x)
  private double log2(double x) {
    // x can't be 0
    return LOG2_E * Math.log(x);
  }

  private void findBinaryMantissa(int exp10, double exp2, long[] mantissa) {
    // pow(2, -exp2): division by 2^exp2 is multiplication by 2^(-exp2) actually
    long[] powerOf2 = this.buffer4x64B;
    powerOfTwo(-exp2, powerOf2);
    long[] product = this.buffer12x32; // use it for the product (M * 10^E / 2^e)
    multUnpacked6x32byPacked(mantissa, powerOf2, product); // product in buff_12x32
    multBuffBy10(product); // "Quasidecimals" are numbers divided by 10
    // The powerOf2[0] is stored as an unsigned value
    if (((long) (powerOf2[0])) != ((long) (-exp10))) {
      // For some combinations of exp2 and exp10, additional multiplication needed
      // (see mant2_from_M_E_e.xls)
      multBuffBy10(product);
    }
    // compensate possible inaccuracy of logarithms used to compute exp2
    exp2 += normalizeMant(product);
    exp2 += EXPONENT_BIAS; // add bias
    // For subnormal values, exp2 <= 0. We just return 0 for them, as they are
    // far from any range we are interested in.
    if (exp2 <= 0) {
      return;
    }
    exp2 += roundUp(product); // round up, may require exponent correction
    if (((long) (exp2)) >= EXPONENT_OF_INFINITY) {
      this.exponent = ((int) (long) (EXPONENT_OF_INFINITY));
    } else {
      this.exponent = ((int) (long) (exp2));
      this.mantHi = ((product[0] << 32L) + product[1]);
      this.mantLo = ((product[2] << 32L) + product[3]);
    }
  }

  // Calculates the required power and returns the result in the quasidecimal format (an array of
  // longs, where result[0] is the decimal exponent of the resulting value, and result[1] --
  // result[3] contain 192 bits of the mantissa divided by ten (so that 8 looks like
  // <pre>{@code {1, 0xCCCC_.._CCCCL, 0xCCCC_.._CCCCL, 0xCCCC_.._CCCDL}}}</pre>
  // uses arrays <b><i>buffer4x64B</b>, buffer6x32A, buffer6x32B, buffer12x32</i></b>,
  // @param exp the power to raise 2 to
  // @param power (result) the value of {@code2^exp}
  private void powerOfTwo(double exp, long[] power) {
    if (exp == 0) {
      array_copy(POS_POWERS_OF_2[0], power);
      return;
    }
    // positive powers of 2 (2^0, 2^1, 2^2, 2^4, 2^8 ... 2^(2^31) )
    long[][] powers = (POS_POWERS_OF_2);
    if (exp < 0) {
      exp = -exp;
      powers = (NEG_POWERS_OF_2); // positive powers of 2 (2^0, 2^-1, 2^-2, 2^-4, 2^-8 ... 2^30)
    }
    // 2^31 = 0x8000_0000L; a single bit that will be shifted right at every iteration
    double currPowOf2 = POW_2_31;
    int idx = 32; // Index in the table of powers
    boolean first_power = true;
    // if exp = b31 * 2^31 + b30 * 2^30 + .. + b0 * 2^0, where b0..b31 are the values of the bits in
    // exp, then 2^exp = 2^b31 * 2^b30 ... * 2^b0. Find the product, using a table of powers of 2.
    while (exp > 0) {
      if (exp >= currPowOf2) { // the current bit in the exponent is 1
        if (first_power) {
          // 4 longs, power[0] -- decimal (?) exponent, power[1..3] -- 192 bits of mantissa
          array_copy((powers)[idx], power);
          first_power = false;
        } else {
          // Multiply by the corresponding power of 2
          multPacked3x64_AndAdjustExponent(power, (powers)[idx], power);
        }
        exp -= currPowOf2;
      }
      idx -= 1;
      currPowOf2 = currPowOf2 * 0.5; // Note: this is exact
    }
  }

  // Copies from into to.
  private void array_copy(long[] source, long[] dest) {
    for (int i = (0); i < ((dest).length); i++) {
      dest[i] = source[i];
    }
  }

  // Multiplies two quasidecimal numbers contained in buffers of 3 x 64 bits with exponents, puts
  // the product to <b><i>buffer4x64B</i></b><br>
  // and returns it. Both each of the buffers and the product contain 4 longs - exponent and 3 x 64
  // bits of mantissa. If the higher word of mantissa of the product is less than
  // 0x1999_9999_9999_9999L (i.e. mantissa is less than 0.1) multiplies mantissa by 10 and adjusts
  // the exponent respectively.
  private void multPacked3x64_AndAdjustExponent(long[] factor1, long[] factor2, long[] result) {
    multPacked3x64_simply(factor1, factor2, this.buffer12x32);
    int expCorr = correctPossibleUnderflow(this.buffer12x32);
    pack_6x32_to_3x64(this.buffer12x32, result);
    // result[0] is a signed int64 value stored in an uint64
    result[0] = factor1[0] + factor2[0] + ((long) (expCorr)); // product.exp = f1.exp + f2.exp
  }

  // Multiplies mantissas of two packed quasidecimal values (each is an array of 4 longs, exponent +
  // 3 x 64 bits of mantissa) Returns the product as unpacked buffer of 12 x 32 (12 x 32 bits of
  // product)
  // uses arrays <b><i>buffer6x32A, buffer6x32B</b></i>
  // @param factor1 an array of longs containing factor 1 as packed quasidecimal
  // @param factor2 an array of longs containing factor 2 as packed quasidecimal
  // @param result an array of 12 longs filled with the product of mantissas
  private void multPacked3x64_simply(long[] factor1, long[] factor2, long[] result) {
    for (int i = (0); i < ((result).length); i++) {
      result[i] = 0L;
    }
    // TODO2 19.01.16 21:23:06 for the next version -- rebuild the table of powers to make the
    // numbers unpacked, to avoid packing/unpacking
    unpack_3x64_to_6x32(factor1, this.buffer6x32A);
    unpack_3x64_to_6x32(factor2, this.buffer6x32B);
    for (int i = (6) - 1; i >= (0); i--) { // compute partial 32-bit products
      for (int j = (6) - 1; j >= (0); j--) {
        long part = this.buffer6x32A[i] * this.buffer6x32B[j];
        result[j + i + 1] = (result[j + i + 1] + (part & LOWER_32_BITS));
        result[j + i] = (result[j + i] + ((part) >>> (32L)));
      }
    }
    // Carry higher bits of the product to the lower bits of the next word
    for (int i = (12) - 1; i >= (1); i--) {
      result[i - 1] = (result[i - 1] + ((result[i]) >>> (32L)));
      result[i] &= LOWER_32_BITS;
    }
  }

  // Corrects possible underflow of the decimal mantissa, passed in in the {@code mantissa}, by
  // multiplying it by a power of ten. The corresponding value to adjust the decimal exponent is
  // returned as the result
  // @param mantissa a buffer containing the mantissa to be corrected
  // @return a corrective (addition) that is needed to adjust the decimal exponent of the number
  private int correctPossibleUnderflow(long[] mantissa) {
    int expCorr = 0;
    while (isLessThanOne(mantissa)) { // Underflow
      multBuffBy10(mantissa);
      expCorr -= 1;
    }
    return expCorr;
  }

  // Checks if the unpacked quasidecimal value held in the given buffer is less than one (in this
  // format, one is represented as { 0x1999_9999L, 0x9999_9999L, 0x9999_9999L,...}
  // @param buffer a buffer containing the value to check
  // @return {@code true}, if the value is less than one
  private boolean isLessThanOne(long[] buffer) {
    if (buffer[0] < 0x1999_9999L) {
      return true;
    }
    if (buffer[0] > 0x1999_9999L) {
      return false;
    }
    // A note regarding the coverage:
    // Multiplying a 128-bit number by another 192-bit number,
    // as well as multiplying of two 192-bit numbers,
    // can never produce 320 (or 384 bits, respectively) of 0x1999_9999L, 0x9999_9999L,
    for (int i = (1); i < ((buffer).length); i++) {
      // so this loop can't be covered entirely
      if (buffer[i] < 0x9999_9999L) {
        return true;
      }
      if (buffer[i] > 0x9999_9999L) {
        return false;
      }
    }
    // and it can never reach this point in real life.
    return false; // Still Java requires the return statement here.
  }

  // Multiplies unpacked 192-bit value by a packed 192-bit factor <br>
  // uses static arrays <b><i>buffer6x32B</i></b>
  // @param factor1 a buffer containing unpacked quasidecimal mantissa (6 x 32 bits)
  // @param factor2 an array of 4 longs containing packed quasidecimal power of two
  // @param product a buffer of at least 12 longs to hold the product
  private void multUnpacked6x32byPacked(long[] factor1, long[] factor2, long[] product) {
    for (int i = (0); i < ((product).length); i++) {
      product[i] = 0L;
    }
    long[] unpacked2 = this.buffer6x32B;
    unpack_3x64_to_6x32(factor2, unpacked2); // It's the powerOf2, with exponent in 0'th word
    int maxFactIdx = (factor1).length;
    for (int i = (maxFactIdx) - 1; i >= (0); i--) { // compute partial 32-bit products
      for (int j = (maxFactIdx) - 1; j >= (0); j--) {
        long part = factor1[i] * unpacked2[j];
        product[j + i + 1] = (product[j + i + 1] + (part & LOWER_32_BITS));
        product[j + i] = (product[j + i] + ((part) >>> (32L)));
      }
    }
    // Carry higher bits of the product to the lower bits of the next word
    for (int i = (12) - 1; i >= (1); i--) {
      product[i - 1] = (product[i - 1] + ((product[i]) >>> (32L)));
      product[i] &= LOWER_32_BITS;
    }
  }

  // Multiplies the unpacked value stored in the given buffer by 10
  // @param buffer contains the unpacked value to multiply (32 least significant bits are used)
  private void multBuffBy10(long[] buffer) {
    int maxIdx = (buffer).length - 1;
    buffer[0] &= LOWER_32_BITS;
    buffer[maxIdx] *= 10L;
    for (int i = (maxIdx) - 1; i >= (0); i--) {
      buffer[i] = (buffer[i] * 10L + ((buffer[i + 1]) >>> (32L)));
      buffer[i + 1] &= LOWER_32_BITS;
    }
  }

  // Makes sure that the (unpacked) mantissa is normalized,
  // i.e. buff[0] contains 1 in bit 32 (the implied integer part) and higher 32 of mantissa in bits
  // 31..0,
  // and buff[1]..buff[4] contain other 96 bits of mantissa in their lower halves:
  // <pre>0x0000_0001_XXXX_XXXXL, 0x0000_0000_XXXX_XXXXL...</pre>
  // If necessary, divides the mantissa by appropriate power of 2 to make it normal.
  // @param mantissa a buffer containing unpacked mantissa
  // @return if the mantissa was not normal initially, a correction that should be added to the
  // result's exponent, or 0 otherwise
  private int normalizeMant(long[] mantissa) {
    int expCorr = 31 - Long.numberOfLeadingZeros(mantissa[0]);
    if (expCorr != 0) {
      divBuffByPower2(mantissa, expCorr);
    }
    return expCorr;
  }

  // Rounds up the contents of the unpacked buffer to 128 bits by adding unity one bit lower than
  // the lowest of these 128 bits. If carry propagates up to bit 33 of buff[0], shifts the buffer
  // rightwards to keep it normalized.
  // @param mantissa the buffer to get rounded
  // @return 1 if the buffer was shifted, 0 otherwise
  private int roundUp(long[] mantissa) {
    // due to the limited precision of the power of 2, a number with exactly half LSB in its
    // mantissa
    // (i.e that would have 0x8000_0000_0000_0000L in bits 128..191 if it were computed precisely),
    // after multiplication by this power of 2, may get erroneous bits 185..191 (counting from the
    // MSB),
    // taking a value from
    // 0xXXXX_XXXX_XXXX_XXXXL 0xXXXX_XXXX_XXXX_XXXXL 0x7FFF_FFFF_FFFF_FFD8L.
    // to
    // 0xXXXX_XXXX_XXXX_XXXXL 0xXXXX_XXXX_XXXX_XXXXL 0x8000_0000_0000_0014L, or something alike.
    // To round it up, we first add
    // 0x0000_0000_0000_0000L 0x0000_0000_0000_0000L 0x0000_0000_0000_0028L, to turn it into
    // 0xXXXX_XXXX_XXXX_XXXXL 0xXXXX_XXXX_XXXX_XXXXL 0x8000_0000_0000_00XXL,
    // and then add
    // 0x0000_0000_0000_0000L 0x0000_0000_0000_0000L 0x8000_0000_0000_0000L, to provide carry to
    // higher bits.
    addToBuff(mantissa, 5, 100L); // to compensate possible inaccuracy
    addToBuff(mantissa, 4, 0x8000_0000L); // round-up, if bits 128..159 >= 0x8000_0000L
    if ((mantissa[0] & (HIGHER_32_BITS << 1L)) != 0L) {
      // carry's got propagated beyond the highest bit
      divBuffByPower2(mantissa, 1);
      return 1;
    }
    return 0;
  }

  // converts 192 most significant bits of the mantissa of a number from an unpacked quasidecimal
  // form (where 32 least significant bits only used) to a packed quasidecimal form (where buff[0]
  // contains the exponent and buff[1]..buff[3] contain 3 x 64 = 192 bits of mantissa)
  // @param unpackedMant a buffer of at least 6 longs containing an unpacked value
  // @param result a buffer of at least 4 long to hold the packed value
  // @return packedQD192 with words 1..3 filled with the packed mantissa. packedQD192[0] is not
  //     affected.
  private void pack_6x32_to_3x64(long[] unpackedMant, long[] result) {
    result[1] = (unpackedMant[0] << 32L) + unpackedMant[1];
    result[2] = (unpackedMant[2] << 32L) + unpackedMant[3];
    result[3] = (unpackedMant[4] << 32L) + unpackedMant[5];
  }

  // Unpacks the mantissa of a 192-bit quasidecimal (4 longs: exp10, mantHi, mantMid, mantLo) to a
  // buffer of 6 longs, where the least significant 32 bits of each long contains respective 32 bits
  // of the mantissa
  // @param qd192 array of 4 longs containing the number to unpack
  // @param buff_6x32 buffer of 6 long to hold the unpacked mantissa
  private void unpack_3x64_to_6x32(long[] qd192, long[] buff_6x32) {
    buff_6x32[0] = ((qd192[1]) >>> (32L));
    buff_6x32[1] = qd192[1] & LOWER_32_BITS;
    buff_6x32[2] = ((qd192[2]) >>> (32L));
    buff_6x32[3] = qd192[2] & LOWER_32_BITS;
    buff_6x32[4] = ((qd192[3]) >>> (32L));
    buff_6x32[5] = qd192[3] & LOWER_32_BITS;
  }

  // Divides the contents of the buffer by 2^exp2<br>
  // (shifts the buffer rightwards by exp2 if the exp2 is positive, and leftwards if it's negative),
  // keeping it unpacked (only lower 32 bits of each element are used, except the buff[0] whose
  // higher half is intended to contain integer part)
  // @param buffer the buffer to divide
  // @param exp2 the exponent of the power of two to divide by, expected to be
  private void divBuffByPower2(long[] buffer, int exp2) {
    int maxIdx = (buffer).length - 1;
    long backShift = ((long) (32 - Math.abs(exp2)));
    if (exp2 > 0) { // Shift to the right
      long exp2Shift = ((long) (exp2));
      for (int i = (maxIdx + 1) - 1; i >= (1); i--) {
        buffer[i] = ((buffer[i]) >>> (exp2Shift)) | ((buffer[i - 1] << backShift) & LOWER_32_BITS);
      }
      buffer[0] = ((buffer[0]) >>> (exp2Shift)); // Preserve the high half of buff[0]
    } else if (exp2 < 0) { // Shift to the left
      long exp2Shift = ((long) (-exp2));
      buffer[0] =
          ((buffer[0] << exp2Shift)
              | ((buffer[1]) >>> (backShift))); // Preserve the high half of buff[0]
      for (int i = (1); i < (maxIdx); i++) {
        buffer[i] =
            (((buffer[i] << exp2Shift) & LOWER_32_BITS) | ((buffer[i + 1]) >>> (backShift)));
      }
      buffer[maxIdx] = (buffer[maxIdx] << exp2Shift) & LOWER_32_BITS;
    }
  }

  // Adds the summand to the idx'th word of the unpacked value stored in the buffer
  // and propagates carry as necessary
  // @param buff the buffer to add the summand to
  // @param idx  the index of the element to which the summand is to be added
  // @param summand the summand to add to the idx'th element of the buffer
  private void addToBuff(long[] buff, int idx, long summand) {
    int maxIdx = idx;
    buff[maxIdx] = (buff[maxIdx] + summand); // Big-endian, the lowest word
    for (int i = (maxIdx + 1) - 1;
        i >= (1);
        i--) { // from the lowest word upwards, except the highest
      if ((buff[i] & HIGHER_32_BITS) != 0L) {
        buff[i] &= LOWER_32_BITS;
        buff[i - 1] += 1L;
      } else {
        break;
      }
    }
  }
}
