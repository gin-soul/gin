package com.gin.stream.state;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Arrays;

/**
 * @author gin
 * @date 2021/2/24
 */
public class MapStateTest {

    public static void main(String[] args) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        //统计每个用户交易金额
        DataStreamSource<Tuple2<String, Long>> tuple2Stream = env.fromCollection(Arrays.asList(
                new Tuple2<String, Long>("luffy", 500L),
                new Tuple2<String, Long>("luffy", 1000L),
                new Tuple2<String, Long>("luffy", 600L),
                new Tuple2<String, Long>("nier", 100L),
                new Tuple2<String, Long>("nier", 200L),
                new Tuple2<String, Long>("nier", 300L)
        ));

        tuple2Stream
                .keyBy(0)
                .map(new RichMapFunction<Tuple2<String, Long>, Tuple2<String, Long>>() {

                    // 这里不适合使用本地变量来保存历史值, 而应该使用状态变量
                    // 状态变量会持久化到外部存储中, 下次flink重启也可以保留
                    MapState<String, Long> userAmountReduceState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        // 定义 ListState 根据key存储list数据
                        // 指定存储状态名称, 指定存储类型
                        MapStateDescriptor<String, Long> stateDescriptor = new MapStateDescriptor<String, Long>("map",
                                TypeInformation.of(new TypeHint<String>() {
                                }),
                                TypeInformation.of(new TypeHint<Long>() {
                                }));
                        // 注册 state
                        userAmountReduceState = getRuntimeContext().getMapState(stateDescriptor);
                    }

                    @Override
                    public Tuple2<String, Long> map(Tuple2<String, Long> value) throws Exception {
                        String name = value.f0;
                        Long amount = value.f1;
                        Long old = userAmountReduceState.get(name) == null ? 0L : userAmountReduceState.get(name);
                        //累计历史值与当前值
                        userAmountReduceState.put(name , old + amount);

                        //输出累计后的值
                        return new Tuple2<>(name, old + amount);
                    }
                }).print();

        try {
            env.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
