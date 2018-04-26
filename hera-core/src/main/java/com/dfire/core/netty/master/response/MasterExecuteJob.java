package com.dfire.core.netty.master.response;

import com.dfire.core.message.Protocol.*;
import com.dfire.core.netty.listener.ResponseListener;
import com.dfire.core.netty.master.MasterContext;
import com.dfire.core.netty.master.MasterWorkHolder;
import com.dfire.core.netty.util.AtomicIncrease;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * @author: <a href="mailto:lingxiao@2dfire.com">凌霄</a>
 * @time: Created in 下午2:26 2018/4/25
 * @desc master向worker发送执行job的指令
 */
@Slf4j
public class MasterExecuteJob {

    public Future<Response> executeJob(final MasterContext context, final MasterWorkHolder holder, ExecuteKind kind, final String id) {
        if (kind == ExecuteKind.DebugKind) {
            return executeDebugJob(context, holder, id);
        }
        return null;
    }


    private Future<Response> executeDebugJob(MasterContext context, MasterWorkHolder holder, String id) {
        holder.getDebugRunning().put(id, false);
        DebugMessage message = DebugMessage.newBuilder().setDebugId(id).build();
        final Request request = Request.newBuilder()
                                    .setRid(AtomicIncrease.getAndIncrement())
                                    .setOperate(Operate.Debug)
                                    .setBody(message.toByteString())
                                    .build();
        Future<Response> future = context.getThreadPool().submit(new Callable<Response>() {
            private Response response;
            @Override
            public Response call() throws Exception {
                final CountDownLatch latch = new CountDownLatch(1);
                context.getHandler().addListener(new ResponseListener() {
                    @Override
                    public void onResponse(Response resp) {
                        if (resp.getRid() == request.getRid()) {
                            context.getHandler().removeListener(this);
                            response = resp;
                            latch.countDown();
                        }
                    }
                    @Override
                    public void onWebResponse(WebResponse resp) {}
                });
                try {
                    latch.await();
                } finally {
                    holder.getDebugRunning().remove(id);
                }

                return response;
            }
        });
        holder.getDebugRunning().remove(id);
        log.info("master send debug command to worker,rid=" + request.getRid()+",debugId=" + id);
        return future;
    }
}