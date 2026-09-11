import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 電卓の計算状態と計算処理を管理するモデルクラス。
 */
public class CalculatorModel {

    // 左辺の値を保持
    private BigDecimal leftOperand;

    // 現在入力中の数値を文字列として保持
    private final StringBuilder currentInput = new StringBuilder();

    // 画面に表示する計算式や結果を文字列として保持
    private final StringBuilder expression = new StringBuilder();

    // 演算子を保持
    private Operator pendingOp;

    // 現在の入力状態を保持
    private InputState state;

    // 計算結果を表示した直後かどうか
    private boolean isResultJustShown;

    // クリア直後かどうか
    private boolean wasJustCleared;

    // 入力できる最大桁数（小数点と先頭のマイナス記号は含めない）
    private static final int MAX_INPUT_LENGTH = 8;

    /**
     * モデルを初期化する。
     */
    public CalculatorModel() {
        clearAll();
        wasJustCleared = false;
    }

    /**
     * 電卓の状態を初期化する。
     */
    public void clearAll() {
        leftOperand = null;
        pendingOp = null;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append("0");
        state = InputState.READY;
        isResultJustShown = false;
        wasJustCleared = true;
    }

    /**
     * 表示内容が空か、0のみかどうかを調べる。
     *
     * @return 表示が空、または0のみならtrue
     */
    public boolean isEmpty() {
        return expression.length() == 0 || "0".contentEquals(expression);
    }

    /**
     * 画面に表示する文字列を返す。
     *
     * @return 表示用文字列
     */
    public String getDisplayText() {
        if (state == InputState.ERROR) {
            return "エラー";
        }

        if (isResultJustShown && leftOperand != null) {
            return FormatterUtil.formatResultForDisplay(leftOperand);
        }

        if (expression.length() == 0) {
            return "0";
        }

        return expression.toString();
    }

    /**
     * 表示文字列を返す。
     *
     * @return 表示用文字列
     */
    public String getText() {
        return getDisplayText();
    }

    /**
     * 何も入力されていない状態で演算子が押下された時、
     * 左辺を0として計算を開始する。
     *
     * @param op 入力された演算子
     */
    public void startWithZero(Operator op) {
        if (op == null) {
            return;
        }

        expression.setLength(0);

        if (op == Operator.SUB) {
            expression.append("0-");
            leftOperand = BigDecimal.ZERO;
            pendingOp = Operator.SUB;
        } else {
            expression.append("0").append(operatorSymbol(op));
            leftOperand = BigDecimal.ZERO;
            pendingOp = op;
        }

        currentInput.setLength(0);
        state = InputState.INPUT_OPERATOR;
        isResultJustShown = false;
        wasJustCleared = false;
    }

    /**
     * 数字を現在の入力に追加する。
     *
     * @param ch 入力された数字
     * @return 追加できた場合はtrue、できなかった場合はfalse
     */
    public boolean appendDigit(char ch) {
        if (!Character.isDigit(ch)) {
            return false;
        }

        if (state == InputState.ERROR) {
            return false;
        }

        if (isResultJustShown) {
            clearAll();
            wasJustCleared = false;
        }

        if (getEffectiveInputLength() >= MAX_INPUT_LENGTH) {
            return false;
        }

        // 先頭が 0 のときに別の数値を押したら置き換える
        if (currentInput.length() == 1 && currentInput.charAt(0) == '0') {
            currentInput.setLength(0);
            currentInput.append(ch);
            syncExpressionWithCurrentInput();
            state = InputState.INPUT_NUMBER;
            isResultJustShown = false;
            wasJustCleared = false;
            return true;
        }

        // -0 の状態では数字を受け付けない（-0. のみ許可）
        if ("-0".equals(currentInput.toString())) {
            return false;
        }

        currentInput.append(ch);

        if ("0".contentEquals(expression)) {
            expression.setLength(0);
        }
        expression.append(ch);

        state = InputState.INPUT_NUMBER;
        isResultJustShown = false;
        wasJustCleared = false;
        return true;
    }

    /**
     * 小数点を現在の入力に追加する。
     *
     * @return 追加できた場合はtrue、できなかった場合はfalse
     */
    public boolean appendDot() {
        if (state == InputState.ERROR) {
            return false;
        }

        // 計算結果表示直後は小数点を受け付けない
        if (isResultJustShown) {
            return false;
        }

        // - の直後には小数点を入力できない
        if ("-".equals(currentInput.toString())) {
            return false;
        }

        if (getEffectiveInputLength() >= MAX_INPUT_LENGTH) {
            return false;
        }

        // すでに小数点が含まれている場合は追加しない
        if (currentInput.indexOf(".") >= 0) {
            return false;
        }

     // 演算子の直後で数値未入力なら、小数点は無視する
        if (state == InputState.INPUT_OPERATOR && currentInput.length() == 0) {
            return false;
        }


        // 入力が空なら 0. を補って小数入力を開始する
        if (currentInput.length() == 0) {
            currentInput.append("0.");
            if ("0".contentEquals(expression)) {
                expression.setLength(0);
            }
            expression.append("0.");

            state = InputState.INPUT_NUMBER;
            isResultJustShown = false;
            wasJustCleared = false;
            return true;
        }

        // -0 のときは -0. を許可する
        if ("-0".equals(currentInput.toString())) {
            currentInput.append('.');
            expression.append('.');
            state = InputState.INPUT_NUMBER;
            isResultJustShown = false;
            wasJustCleared = false;
            return true;
        }

        currentInput.append('.');
        expression.append('.');
        state = InputState.INPUT_NUMBER;
        isResultJustShown = false;
        wasJustCleared = false;
        return true;
    }

    /**
     * 演算子を設定する。
     *
     * @param op 入力された演算子
     */
    public void setOperator(Operator op) {
        inputOperator(op);
    }

    /**
     * 演算子を入力し、必要に応じて途中計算を行う。
     *
     * @param op 入力された演算子
     */
    public void inputOperator(Operator op) {
        if (state == InputState.ERROR || op == null) {
            return;
        }

        // クリア直後はマイナス以外の演算子入力を無効にする
        if (wasJustCleared && state == InputState.READY && op != Operator.SUB) {
            return;
        }

        if (isResultJustShown) {
            isResultJustShown = false;
            if (leftOperand != null) {
                pendingOp = op;
                expression.setLength(0);
                expression.append(FormatterUtil.formatResultForDisplay(leftOperand));
                appendOrReplaceOperator(op);
                currentInput.setLength(0);
                state = InputState.INPUT_OPERATOR;
                wasJustCleared = false;
                return;
            }
        }

        // 何も入力されていない状態で - が押されたら負数入力を開始する
        if (state == InputState.READY && currentInput.length() == 0 && leftOperand == null) {
            if (op == Operator.SUB) {
                currentInput.setLength(0);
                currentInput.append("-");
                expression.setLength(0);
                expression.append("-");
                state = InputState.INPUT_NUMBER;
                isResultJustShown = false;
                wasJustCleared = false;
            } else {
                startWithZero(op);
            }
            return;
        }

        // 演算子が連続入力された場合は最後の演算子を置き換える
        if (state == InputState.INPUT_OPERATOR) {
            appendOrReplaceOperator(op);
            pendingOp = op;
            wasJustCleared = false;
            return;
        }

        if (currentInput.length() == 0) {
            return;
        }

        if ("-".equals(currentInput.toString())) {
            return;
        }

        // -0 の状態では演算子を受け付けない
        if ("-0".equals(currentInput.toString())) {
            return;
        }

        BigDecimal currentValue = safeParse(currentInput.toString());

        if (leftOperand == null) {
            leftOperand = currentValue;
        } else if (pendingOp != null) {
            BigDecimal applied = apply(leftOperand, currentValue, pendingOp);
            if (state == InputState.ERROR) {
                return;
            }
            leftOperand = applied;
        }

        pendingOp = op;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append(FormatterUtil.formatResultForDisplay(leftOperand));
        appendOrReplaceOperator(op);

        state = InputState.INPUT_OPERATOR;
        isResultJustShown = false;
        wasJustCleared = false;
    }

    /**
     * イコール操作を実行する。
     */
    public void equalsOp() {
        try {
            evaluate();
        } catch (Exception e) {
            ErrorHandler.handle(e);
            state = InputState.ERROR;
            currentInput.setLength(0);
            expression.setLength(0);
            expression.append("エラー");
        }
    }

    /**
     * 現在の左辺・右辺・演算子を使って計算を確定する。
     */
    public void evaluate() {
        if (state == InputState.ERROR) {
            return;
        }

        if (pendingOp == null || leftOperand == null || currentInput.length() == 0) {
            return;
        }

        if ("-".equals(currentInput.toString())) {
            return;
        }

        // -0 の状態では計算を確定しない
        if ("-0".equals(currentInput.toString())) {
            return;
        }

        BigDecimal right = safeParse(currentInput.toString());
        BigDecimal result = apply(leftOperand, right, pendingOp);

        if (state == InputState.ERROR) {
            return;
        }

        leftOperand = result;
        pendingOp = null;
        currentInput.setLength(0);
        expression.setLength(0);
        expression.append(FormatterUtil.formatResultForDisplay(result));
        state = InputState.READY;
        isResultJustShown = true;
        wasJustCleared = false;
    }

    // 左辺・右辺に対して演算子に応じた計算結果を返す
    private BigDecimal apply(BigDecimal left, BigDecimal right, Operator op) {
        try {
            switch (op) {
                case ADD:
                    return left.add(right);
                case SUB:
                    return left.subtract(right);
                case MUL:
                    return left.multiply(right);
                case DIV:
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("0で割ることはできません");
                    }
                    return left.divide(right, 16, RoundingMode.HALF_UP).stripTrailingZeros();
                default:
                    return right;
            }
        } catch (Exception e) {
            ErrorHandler.handle(e);
            state = InputState.ERROR;
            leftOperand = null;
            pendingOp = null;
            currentInput.setLength(0);
            expression.setLength(0);
            expression.append("エラー");
            isResultJustShown = false;
            wasJustCleared = false;
            return BigDecimal.ZERO;
        }
    }

    // 文字列を BigDecimal に変換する。不完全な入力は 0 として扱う
    private BigDecimal safeParse(String text) {
        if (text == null || text.isEmpty()
                || "-".equals(text)
                || ".".equals(text)
                || "-.".equals(text)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text);
    }

    // 実際の入力桁数を返す。小数点と先頭のマイナス記号は数えない
    private int getEffectiveInputLength() {
        int count = 0;

        for (int i = 0; i < currentInput.length(); i++) {
            char ch = currentInput.charAt(i);
            if (Character.isDigit(ch)) {
                count++;
            }
        }

        return count;
    }

    // currentInput の内容に合わせて expression を更新する
    private void syncExpressionWithCurrentInput() {
        expression.setLength(0);

        if (leftOperand != null && pendingOp != null) {
            expression.append(FormatterUtil.formatResultForDisplay(leftOperand));
            expression.append(operatorSymbol(pendingOp));
            expression.append(currentInput);
            return;
        }

        expression.append(currentInput);
    }

    // 式の末尾に演算子を追加し、すでにあれば置き換える
    private void appendOrReplaceOperator(Operator op) {
        char symbol = operatorSymbol(op);

        if (expression.length() == 0) {
            expression.append(symbol);
            return;
        }

        int lastIndex = expression.length() - 1;
        char lastChar = expression.charAt(lastIndex);

        if (isOperatorChar(lastChar)) {
            expression.setCharAt(lastIndex, symbol);
            return;
        }

        expression.append(symbol);
    }

    // 指定した文字が演算子かどうかを判定する
    private boolean isOperatorChar(char ch) {
        return ch == '+' || ch == '-' || ch == '×' || ch == '÷';
    }

    // 演算子を表示用の記号に変換する
    private char operatorSymbol(Operator op) {
        switch (op) {
            case ADD:
                return '+';
            case SUB:
                return '-';
            case MUL:
                return '×';
            case DIV:
                return '÷';
            default:
                return '?';
        }
    }
}
